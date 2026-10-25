/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.businessdata.repository;

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.ColumnKind;
import cherry.mastersmith.businessdata.domain.ColumnValueReader;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DB の型と受け渡しの型の対応・要求の文字を型として読む・型ごとのセッター・結果の値を文字にする、の1か所（BR4.7・BR6.2・BR7.1〜
 * BR7.3・BR7.6・BR7.8、NFR3.3、NFR 設計の F3: A・レビュー R-02）。U2 も同じ部品を使う。
 *
 * <p>Spring の部品にしない（{@code TraceAspect} が引数の要求の値を TRACE に出さないように）。読み取りと変換の非検査の例外は
 * メソッドの中で受けて結果の型にし、外へ出さない。
 */
public final class ColumnValueCodec implements ColumnValueReader {

    /** 一覧の値の文字数の上限（コードポイント。NFR2.5）。 */
    public static final int LIST_TEXT_LIMIT = 100;

    /** 詳細の値の文字数の上限（コードポイント。NFR2.5）。 */
    public static final int DETAIL_TEXT_LIMIT = 10_000;

    private static final Set<String> STRING_TYPES = Set.of(
            "char",
            "varchar",
            "nchar",
            "nvarchar",
            "bpchar",
            "character",
            "character varying",
            "text",
            "tinytext",
            "mediumtext",
            "longtext",
            "clob");

    private static final Set<String> INTEGER_TYPES =
            Set.of("tinyint", "smallint", "mediumint", "int", "integer", "int2", "int4");

    private static final Set<String> BIG_INTEGER_TYPES = Set.of("bigint", "int8");

    private static final Set<String> DECIMAL_TYPES = Set.of(
            "decimal", "dec", "fixed", "numeric", "float", "double", "double precision", "real", "float4", "float8");

    private static final Set<String> BOOLEAN_TYPES = Set.of("bool", "boolean");

    private static final Set<String> BINARY_TYPES =
            Set.of("binary", "varbinary", "tinyblob", "blob", "mediumblob", "longblob", "bytea");

    /** MySQL・MariaDB の空間の型（BINARY と同じ扱い。F3: A）。 */
    private static final Set<String> SPATIAL_TYPES = Set.of(
            "geometry",
            "point",
            "linestring",
            "polygon",
            "multipoint",
            "multilinestring",
            "multipolygon",
            "geometrycollection",
            "geomcollection");

    /** 整数の型ごとの値の範囲（符号なしの型も受け付けるよう、符号つきの下限と符号なしの上限の和）。 */
    private static final Map<String, long[]> INTEGER_RANGES = Map.of(
            "tinyint", new long[] {-128L, 255L},
            "smallint", new long[] {-32_768L, 65_535L},
            "int2", new long[] {-32_768L, 32_767L},
            "mediumint", new long[] {-8_388_608L, 16_777_215L},
            "int", new long[] {Integer.MIN_VALUE, 4_294_967_295L},
            "integer", new long[] {Integer.MIN_VALUE, 4_294_967_295L},
            "int4", new long[] {Integer.MIN_VALUE, Integer.MAX_VALUE});

    /** 符号なしの整数の型ごとの値の範囲（型の名前に {@code unsigned} を含むとき。下限は 0）。 */
    private static final Map<String, long[]> UNSIGNED_INTEGER_RANGES = Map.of(
            "tinyint", new long[] {0L, 255L},
            "smallint", new long[] {0L, 65_535L},
            "mediumint", new long[] {0L, 16_777_215L},
            "int", new long[] {0L, 4_294_967_295L},
            "integer", new long[] {0L, 4_294_967_295L});

    /** 型の名前の中の符号なしの印（{@link RowDialect#baseName} は括弧の後ろの {@code unsigned} も残す）。 */
    private static final Pattern UNSIGNED = Pattern.compile("(?:^|\\s)unsigned(?:\\s|$)");

    private static final BigInteger BIG_INTEGER_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    private static final BigInteger BIG_INTEGER_MAX = new BigInteger("18446744073709551615");

    /** 符号つきの 64 ビットの整数の上限（PostgreSQL の int8）。 */
    private static final BigInteger BIG_INTEGER_SIGNED_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    /** 符号なしが無い PostgreSQL の 64 ビットの整数の型の名前。 */
    private static final String POSTGRES_BIG_INTEGER = "int8";

    private static final Pattern INTEGER_TEXT = Pattern.compile("[+-]?[0-9]+");

    private static final Pattern DECIMAL_TEXT = Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");

    private static final Pattern TINYINT_1 = Pattern.compile("tinyint\\s*\\(\\s*1\\s*\\).*");

    private static final Pattern BIT_LENGTH = Pattern.compile("bit\\s*\\(\\s*([0-9]+)\\s*\\).*");

    /** 日時・時刻の型の名前の精度（{@code timestamp(3)}・{@code datetime(6)} など）。 */
    private static final Pattern FRACTION_IN_NAME = Pattern.compile("\\(\\s*([0-9]+)\\s*\\)");

    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(TIME)
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter DATE_TIME_UTC = new DateTimeFormatterBuilder()
            .append(DATE_TIME)
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter TIME_UTC = new DateTimeFormatterBuilder()
            .append(TIME)
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter(Locale.ROOT);

    /**
     * DB の種類と DSL のカラムから、受け渡しの型を決める（BR7.1、F3: A）。DB の種類が分からないときは、3種類のどれかで BINARY なら
     * BINARY、3種類で同じならその型、違えば OTHER にする（狭める向き）。
     *
     * @param product DB の種類（分からなければ空）
     * @param column DSL のカラム
     * @return 受け渡しの型
     */
    public static ValueType valueType(Optional<DatabaseProduct> product, DslColumn column) {
        if (product.isPresent()) {
            return valueType(product.get(), column);
        }
        Set<ValueType> types = EnumSet.noneOf(ValueType.class);
        for (DatabaseProduct each : DatabaseProduct.values()) {
            types.add(valueType(each, column));
        }
        if (types.contains(ValueType.BINARY)) {
            return ValueType.BINARY;
        }
        return types.size() == 1 ? types.iterator().next() : ValueType.OTHER;
    }

    /**
     * DB の種類と DSL のカラムから、受け渡しの型と完全一致に使えるかを決める（BR7.1・BR7.9）。
     *
     * @param product DB の種類（分からなければ空）
     * @param column DSL のカラム
     * @return 型
     */
    public static ColumnKind kind(Optional<DatabaseProduct> product, DslColumn column) {
        ValueType type = valueType(product, column);
        return new ColumnKind(
                type,
                RowDialect.equalsSupported(product, column.dbType().name()),
                fractionDigits(product, column, type));
    }

    /**
     * 日時・時刻の型の秒の端数の桁の上限を決める（U2、NFR 設計 Q6: A、generation-notes.md の G1）。型の名前の {@code (n)} → DSL の精度 →
     * DB の種類の既定（PostgreSQL 6、MySQL・MariaDB 0）の順。DB の種類が分からないときは 0（狭める向き）。ほかの型は null。
     *
     * @param product DB の種類（分からなければ空）
     * @param column DSL のカラム
     * @param type 受け渡しの型
     * @return 秒の端数の桁の上限（日時・時刻の型でなければ null）
     */
    static Integer fractionDigits(Optional<DatabaseProduct> product, DslColumn column, ValueType type) {
        if (type != ValueType.DATETIME
                && type != ValueType.DATETIME_TZ
                && type != ValueType.TIME
                && type != ValueType.TIME_TZ) {
            return null;
        }
        Matcher matcher = FRACTION_IN_NAME.matcher(column.dbType().name().toLowerCase(Locale.ROOT));
        int digits;
        if (matcher.find()) {
            digits = parseOrZero(matcher.group(1));
        } else if (column.dbType().precision() != null) {
            digits = column.dbType().precision();
        } else {
            digits = product.map(p -> p == DatabaseProduct.POSTGRESQL ? 6 : 0).orElse(0);
        }
        return Math.min(Math.max(digits, 0), 9);
    }

    /**
     * 型として読んだ値を、結果の値と同じ形の文字にする（U2 の作成した主キーの応答・監査に使う）。
     *
     * @param value 値
     * @return 文字（NULL は null）
     */
    public static String format(BoundValue value) {
        Object raw = value.value();
        if (raw == null) {
            return null;
        }
        return switch (value.valueType()) {
            case STRING, OTHER -> (String) raw;
            case INTEGER -> Long.toString((Long) raw);
            case BIG_INTEGER, DECIMAL -> ((BigDecimal) raw).toPlainString();
            case BOOLEAN -> Boolean.toString((Boolean) raw);
            case DATE -> DateTimeFormatter.ISO_LOCAL_DATE.format((LocalDate) raw);
            case DATETIME -> DATE_TIME.format((LocalDateTime) raw);
            case TIME -> TIME.format((LocalTime) raw);
            case DATETIME_TZ -> DATE_TIME_UTC.format(((OffsetDateTime) raw).withOffsetSameInstant(ZoneOffset.UTC));
            case TIME_TZ -> TIME_UTC.format(((OffsetTime) raw).withOffsetSameInstant(ZoneOffset.UTC));
            case BINARY -> throw new IllegalArgumentException("BINARY の値は文字にしません");
        };
    }

    /**
     * DB の種類が分かるときの受け渡しの型（BR7.1）。
     *
     * <p>MySQL・MariaDB の {@code tinyint(1)} は BOOLEAN。既定の DSL の生成は型の名前に {@code DATA_TYPE}（{@code tinyint}）だけを
     * 写し {@code (1)} を落とすため、フォーム部品が checkbox・switch の {@code tinyint} も BOOLEAN とする（generation-notes.md）。
     */
    static ValueType valueType(DatabaseProduct product, DslColumn column) {
        String name = column.dbType().name().strip().toLowerCase(Locale.ROOT);
        String base = RowDialect.baseName(name);
        int space = base.indexOf(' ');
        String word = space < 0 ? base : base.substring(0, space);
        boolean mysqlFamily = product != DatabaseProduct.POSTGRESQL;
        if (BOOLEAN_TYPES.contains(base)) {
            return ValueType.BOOLEAN;
        }
        if (mysqlFamily) {
            if (word.equals("tinyint")
                    && (TINYINT_1.matcher(name).matches()
                            || column.formPart() == FormPart.CHECKBOX
                            || column.formPart() == FormPart.SWITCH)) {
                return ValueType.BOOLEAN;
            }
            if (word.equals("bit")) {
                return bitLength(name, column) == 1 ? ValueType.BOOLEAN : ValueType.BINARY;
            }
            if (SPATIAL_TYPES.contains(word)) {
                return ValueType.BINARY;
            }
            if (word.equals("timestamp")) {
                return ValueType.DATETIME_TZ;
            }
            if (word.equals("datetime")) {
                return ValueType.DATETIME;
            }
        } else {
            if (base.equals("timestamptz") || base.equals("timestamp with time zone")) {
                return ValueType.DATETIME_TZ;
            }
            if (base.equals("timestamp") || base.equals("timestamp without time zone")) {
                return ValueType.DATETIME;
            }
            if (base.equals("timetz") || base.equals("time with time zone")) {
                return ValueType.TIME_TZ;
            }
            if (base.equals("time without time zone")) {
                return ValueType.TIME;
            }
        }
        if (STRING_TYPES.contains(base)) {
            return ValueType.STRING;
        }
        if (INTEGER_TYPES.contains(word)) {
            return ValueType.INTEGER;
        }
        if (BIG_INTEGER_TYPES.contains(word)) {
            return ValueType.BIG_INTEGER;
        }
        if (DECIMAL_TYPES.contains(base) || DECIMAL_TYPES.contains(word)) {
            return ValueType.DECIMAL;
        }
        if (base.equals("date")) {
            return ValueType.DATE;
        }
        if (base.equals("time")) {
            return ValueType.TIME;
        }
        if (BINARY_TYPES.contains(base)) {
            return ValueType.BINARY;
        }
        return ValueType.OTHER;
    }

    /** {@code bit} の長さ（名前の {@code (n)}・精度・長さの順。どれも無ければ MySQL の既定の 1）。 */
    private static int bitLength(String name, DslColumn column) {
        Matcher matcher = BIT_LENGTH.matcher(name);
        if (matcher.matches()) {
            return parseOrZero(matcher.group(1));
        }
        if (column.dbType().precision() != null) {
            return column.dbType().precision();
        }
        if (column.dbType().length() != null) {
            return column.dbType().length();
        }
        return 1;
    }

    private static int parseOrZero(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 要求の文字をカラムの型として読む（BR4.7・BR6.2、NFR3.3）。読めない値・桁あふれは空。時差つきの型はオフセットが無ければ UTC、
     * {@code Z}・{@code ±hh:mm} も受け付けて UTC にそろえる。
     */
    @Override
    public Optional<BoundValue> read(ColumnAccess column, String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(parse(column.valueType(), RowDialect.baseName(column.dbTypeName()), text));
        } catch (RuntimeException e) {
            // 読めない値（NumberFormatException・DateTimeParseException・ArithmeticException など）は入力の誤りとして空にする。
            return Optional.empty();
        }
    }

    private static BoundValue parse(ValueType type, String base, String text) {
        return switch (type) {
            case STRING, OTHER -> new BoundValue(type, text);
            case INTEGER -> parseInteger(base, text);
            case BIG_INTEGER -> parseBigInteger(base, text);
            case DECIMAL -> DECIMAL_TEXT.matcher(text).matches() ? new BoundValue(type, new BigDecimal(text)) : null;
            case BOOLEAN -> parseBoolean(text);
            case DATE -> new BoundValue(type, LocalDate.parse(text));
            case DATETIME -> new BoundValue(type, LocalDateTime.parse(text));
            case TIME -> new BoundValue(type, LocalTime.parse(text));
            case DATETIME_TZ -> new BoundValue(type, parseDateTimeTz(text));
            case TIME_TZ -> new BoundValue(type, parseTimeTz(text));
            case BINARY -> null;
        };
    }

    private static BoundValue parseInteger(String base, String text) {
        if (!INTEGER_TEXT.matcher(text).matches()) {
            return null;
        }
        long value = Long.parseLong(text);
        int space = base.indexOf(' ');
        String word = space < 0 ? base : base.substring(0, space);
        // 型の名前に unsigned を含むときだけ符号なしの範囲（下限 0）で厳しく判定する。含まない名前は、DSL の型の名前に符号の情報が
        // 無い既存の DSL（符号なしの列も int）を壊さないよう、従来の「符号つきの下限と符号なしの上限の和」のまま（FR3.1、計画の R-08 案 A）。
        long[] range = (isUnsigned(base) ? UNSIGNED_INTEGER_RANGES : INTEGER_RANGES).get(word);
        if (range == null) {
            range = INTEGER_RANGES.get(word);
        }
        if (range != null && (value < range[0] || value > range[1])) {
            return null;
        }
        return new BoundValue(ValueType.INTEGER, value);
    }

    private static BoundValue parseBigInteger(String base, String text) {
        if (!INTEGER_TEXT.matcher(text).matches()) {
            return null;
        }
        BigInteger value = new BigInteger(text);
        // PostgreSQL の int8 には符号なしが無いため、符号つきの上限を当てる（U2 の BR3.5）。MySQL・MariaDB の bigint は、DSL の型の
        // 名前から符号なしかが分からないため、符号つきの下限と符号なしの上限の和のまま（U2 の generation-notes.md の G13）。
        BigInteger max = POSTGRES_BIG_INTEGER.equals(base) ? BIG_INTEGER_SIGNED_MAX : BIG_INTEGER_MAX;
        // 型の名前に unsigned を含むときは下限を 0 にする（FR3.1）。
        BigInteger min = isUnsigned(base) ? BigInteger.ZERO : BIG_INTEGER_MIN;
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            return null;
        }
        return new BoundValue(ValueType.BIG_INTEGER, new BigDecimal(value));
    }

    private static boolean isUnsigned(String base) {
        return UNSIGNED.matcher(base).find();
    }

    private static BoundValue parseBoolean(String text) {
        if (text.equals("true")) {
            return new BoundValue(ValueType.BOOLEAN, Boolean.TRUE);
        }
        if (text.equals("false")) {
            return new BoundValue(ValueType.BOOLEAN, Boolean.FALSE);
        }
        return null;
    }

    private static OffsetDateTime parseDateTimeTz(String text) {
        boolean hasOffset = text.endsWith("Z") || text.endsWith("z") || text.matches(".*[+-][0-9]{2}:[0-9]{2}$");
        OffsetDateTime value = hasOffset
                ? OffsetDateTime.parse(text.replace('z', 'Z'))
                : LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static OffsetTime parseTimeTz(String text) {
        boolean hasOffset = text.endsWith("Z") || text.endsWith("z") || text.matches(".*[+-][0-9]{2}:[0-9]{2}$");
        OffsetTime value = hasOffset
                ? OffsetTime.parse(text.replace('z', 'Z'))
                : LocalTime.parse(text).atOffset(ZoneOffset.UTC);
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    /**
     * 型として読んだ値を、型ごとのセッターでバインドする（BR7.8。OTHER と時差つきの日時は方言に任せる）。
     *
     * @param statement 文
     * @param index 位置
     * @param value 値
     * @param dialect 方言
     * @throws SQLException バインドの失敗
     */
    public void bind(PreparedStatement statement, int index, BoundValue value, RowDialect dialect) throws SQLException {
        Object raw = value.value();
        if (raw == null) {
            statement.setNull(index, sqlType(value.valueType()));
            return;
        }
        switch (value.valueType()) {
            case STRING -> statement.setString(index, (String) raw);
            case INTEGER -> statement.setLong(index, (Long) raw);
            case BIG_INTEGER, DECIMAL -> statement.setBigDecimal(index, (BigDecimal) raw);
            case BOOLEAN -> statement.setBoolean(index, (Boolean) raw);
            case DATE, DATETIME, TIME, TIME_TZ -> statement.setObject(index, raw);
            case DATETIME_TZ -> dialect.bindDateTimeTz(statement, index, (OffsetDateTime) raw);
            case OTHER -> dialect.bindOther(statement, index, (String) raw);
            case BINARY -> throw new IllegalArgumentException("BINARY の値はバインドしません");
        }
    }

    private static int sqlType(ValueType type) {
        return switch (type) {
            case STRING -> Types.VARCHAR;
            case INTEGER -> Types.BIGINT;
            case BIG_INTEGER, DECIMAL -> Types.NUMERIC;
            case BOOLEAN -> Types.BOOLEAN;
            case DATE -> Types.DATE;
            case DATETIME -> Types.TIMESTAMP;
            case TIME -> Types.TIME;
            case DATETIME_TZ -> Types.TIMESTAMP_WITH_TIMEZONE;
            case TIME_TZ -> Types.TIME_WITH_TIMEZONE;
            case BINARY, OTHER -> Types.OTHER;
        };
    }

    /**
     * 結果の値を読み、文字にする（BR7.2・BR7.3・BR7.6）。STRING と OTHER は、上限の文字数（コードポイント）を超えたら先頭の上限の分
     * だけにして切った印を付ける。型の変換の非検査の例外は {@link CellValue.Unreadable} にする（例外を外へ出さない）。
     *
     * @param resultSet 結果
     * @param index 位置
     * @param type 受け渡しの型（BINARY は呼ぶ前に外している）
     * @param limit 文字数の上限（切らないなら {@link Integer#MAX_VALUE}）
     * @param dialect 方言
     * @return 文字にした値
     * @throws SQLException 読み取りの失敗（時間切れ・接続の失敗を含む）
     */
    public CellValue readCell(ResultSet resultSet, int index, ValueType type, int limit, RowDialect dialect)
            throws SQLException {
        try {
            return switch (type) {
                case STRING, OTHER -> truncate(resultSet.getString(index), limit);
                case INTEGER -> {
                    long value = resultSet.getLong(index);
                    yield new CellValue.Text(resultSet.wasNull() ? null : Long.toString(value), false);
                }
                case BIG_INTEGER, DECIMAL -> {
                    BigDecimal value = resultSet.getBigDecimal(index);
                    yield new CellValue.Text(value == null ? null : value.toPlainString(), false);
                }
                case BOOLEAN -> {
                    if (dialect.readsBooleanAsInteger()) {
                        yield readBooleanAsInteger(resultSet, index);
                    }
                    boolean value = resultSet.getBoolean(index);
                    yield new CellValue.Text(resultSet.wasNull() ? null : Boolean.toString(value), false);
                }
                case DATE ->
                    text(
                            dialect.readsTemporalAsText()
                                    ? parseText(resultSet.getString(index), LocalDate::parse)
                                    : resultSet.getObject(index, LocalDate.class),
                            DateTimeFormatter.ISO_LOCAL_DATE);
                case DATETIME ->
                    text(
                            dialect.readsTemporalAsText()
                                    ? parseText(resultSet.getString(index), ColumnValueCodec::parseLocalDateTime)
                                    : resultSet.getObject(index, LocalDateTime.class),
                            DATE_TIME);
                case TIME ->
                    text(
                            dialect.readsTemporalAsText()
                                    ? parseText(resultSet.getString(index), LocalTime::parse)
                                    : resultSet.getObject(index, LocalTime.class),
                            TIME);
                case DATETIME_TZ -> text(dialect.readDateTimeTz(resultSet, index), DATE_TIME_UTC);
                case TIME_TZ -> {
                    OffsetTime value = resultSet.getObject(index, OffsetTime.class);
                    yield text(value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC), TIME_UTC);
                }
                case BINARY -> throw new IllegalArgumentException("BINARY の値は読みません");
            };
        } catch (RuntimeException e) {
            return new CellValue.Unreadable(e.getClass().getName());
        }
    }

    /**
     * 真偽値を整数として読む。0 は false、1 は true、それ以外の値は読めない値（{@link IllegalStateException}。呼び出しが
     * {@link CellValue.Unreadable} にする）。値は例外に入れない。
     */
    private static CellValue readBooleanAsInteger(ResultSet resultSet, int index) throws SQLException {
        long value = resultSet.getLong(index);
        if (resultSet.wasNull()) {
            return new CellValue.Text(null, false);
        }
        if (value != 0L && value != 1L) {
            throw new IllegalStateException("真偽値として読めない値です");
        }
        return new CellValue.Text(Boolean.toString(value == 1L), false);
    }

    private static <T> T parseText(String text, Function<String, T> parser) {
        return text == null ? null : parser.apply(text);
    }

    /**
     * MySQL・MariaDB が文字で送る日時（{@code 2026-03-08 02:30:00.123456}）を読む。区切りの空白と ISO の {@code T} の両方を受け付ける。
     * ゼロの日時・範囲の外の値は {@code DateTimeParseException}。
     *
     * @param text 文字
     * @return 日時
     */
    static LocalDateTime parseLocalDateTime(String text) {
        return LocalDateTime.parse(text.replace(' ', 'T'));
    }

    private static CellValue text(TemporalAccessor value, DateTimeFormatter formatter) {
        return new CellValue.Text(value == null ? null : formatter.format(value), false);
    }

    /**
     * 文字を上限のコードポイントで切る（BR7.6）。
     *
     * @param value 文字（null を許す）
     * @param limit 上限
     * @return 文字にした値
     */
    static CellValue.Text truncate(String value, int limit) {
        if (value == null || value.length() <= limit || value.codePointCount(0, value.length()) <= limit) {
            return new CellValue.Text(value, false);
        }
        return new CellValue.Text(value.substring(0, value.offsetByCodePoints(0, limit)), true);
    }
}
