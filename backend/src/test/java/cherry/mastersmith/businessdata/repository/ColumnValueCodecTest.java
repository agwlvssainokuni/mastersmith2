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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.access;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.math.BigDecimal;
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
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 型の対応・型として読む・型ごとのセッター・値を文字にする（BR7.1〜BR7.3・BR7.6・BR7.8、NFR3.3、F3: A、R-02）の単体テスト。 */
class ColumnValueCodecTest {

    private final ColumnValueCodec codec = new ColumnValueCodec();

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "MYSQL, tinyint(1), BOOLEAN",
        "MYSQL, tinyint, INTEGER",
        "MARIADB, bit(1), BOOLEAN",
        "MYSQL, bit(8), BINARY",
        "MARIADB, geometry, BINARY",
        "MYSQL, point, BINARY",
        "MYSQL, timestamp, DATETIME_TZ",
        "MYSQL, datetime, DATETIME",
        "MYSQL, year, OTHER",
        "MYSQL, int unsigned, INTEGER",
        "MYSQL, BIGINT, BIG_INTEGER",
        "MYSQL, 'decimal(38,10)', DECIMAL",
        "MYSQL, longblob, BINARY",
        "MYSQL, enum, OTHER",
        "POSTGRESQL, timestamptz, DATETIME_TZ",
        "POSTGRESQL, timestamp, DATETIME",
        "POSTGRESQL, timetz, TIME_TZ",
        "POSTGRESQL, bool, BOOLEAN",
        "POSTGRESQL, bit, OTHER",
        "POSTGRESQL, point, OTHER",
        "POSTGRESQL, bytea, BINARY",
        "POSTGRESQL, bpchar, STRING",
        "POSTGRESQL, int8, BIG_INTEGER",
        "POSTGRESQL, uuid, OTHER"
    })
    @DisplayName("the DSL type name is mapped per database, ignoring case")
    void typeMapping(DatabaseProduct product, String typeName, ValueType expected) {
        assertThat(ColumnValueCodec.valueType(
                        Optional.of(product), column("c").dbType(typeName).build()))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName(
            "a generated tinyint with a checkbox and a bit with precision 1 are booleans, and unknown products narrow")
    void generatedDslAndUnknownProduct() {
        DslColumn checkbox =
                column("c").dbType("tinyint").form(FormPart.CHECKBOX).build();
        DslColumn bitByPrecision = column("c").dbType("bit", 1).build();
        DslColumn bitWide = column("c").dbType("bit", 16).build();

        assertThat(ColumnValueCodec.valueType(Optional.of(DatabaseProduct.MYSQL), checkbox))
                .isEqualTo(ValueType.BOOLEAN);
        assertThat(ColumnValueCodec.valueType(Optional.of(DatabaseProduct.MYSQL), bitByPrecision))
                .isEqualTo(ValueType.BOOLEAN);
        assertThat(ColumnValueCodec.valueType(Optional.of(DatabaseProduct.MARIADB), bitWide))
                .isEqualTo(ValueType.BINARY);
        assertThat(ColumnValueCodec.valueType(Optional.empty(), bitWide))
                .as("どれかで BINARY なら BINARY")
                .isEqualTo(ValueType.BINARY);
        assertThat(ColumnValueCodec.valueType(
                        Optional.empty(), column("c").dbType("timestamp").build()))
                .as("DB で違えば OTHER")
                .isEqualTo(ValueType.OTHER);
        assertThat(ColumnValueCodec.valueType(
                        Optional.empty(), column("c").dbType("varchar").build()))
                .isEqualTo(ValueType.STRING);
        assertThat(ColumnValueCodec.kind(
                                Optional.of(DatabaseProduct.MYSQL),
                                column("c").dbType("json").build())
                        .equalsSupported())
                .isFalse();
        assertThat(ColumnValueCodec.kind(
                                Optional.of(DatabaseProduct.MARIADB),
                                column("c").dbType("json").build())
                        .equalsSupported())
                .isTrue();
    }

    @Test
    @DisplayName("numbers keep every digit, out-of-range integers are rejected and malformed numbers are empty")
    void readNumbers() {
        assertThat(codec.read(access(ValueType.INTEGER, "int"), "4294967295"))
                .map(BoundValue::value)
                .contains(4_294_967_295L);
        assertThat(codec.read(access(ValueType.INTEGER, "smallint"), "65536")).isEmpty();
        assertThat(codec.read(access(ValueType.INTEGER, "int4"), "2147483648")).isEmpty();
        assertThat(codec.read(access(ValueType.INTEGER, "int"), "1e3")).isEmpty();
        assertThat(codec.read(access(ValueType.INTEGER, "int"), "99999999999999999999"))
                .isEmpty();
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint"), "18446744073709551615"))
                .map(BoundValue::value)
                .contains(new BigDecimal("18446744073709551615"));
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint"), "18446744073709551616"))
                .isEmpty();
        // PostgreSQL の int8 は符号つきの範囲だけ（U2 の BR3.5。上限ちょうどは受け付け、1 を超えると空）。
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "int8"), "9223372036854775807"))
                .map(BoundValue::value)
                .contains(new BigDecimal("9223372036854775807"));
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "int8"), "9223372036854775808"))
                .isEmpty();
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "int8"), "-9223372036854775808"))
                .map(BoundValue::value)
                .contains(new BigDecimal("-9223372036854775808"));
        assertThat(codec.read(access(ValueType.DECIMAL, "numeric"), "-1234567890123456789012345678.0123456789"))
                .map(b -> ((BigDecimal) b.value()).toPlainString())
                .contains("-1234567890123456789012345678.0123456789");
        assertThat(codec.read(access(ValueType.DECIMAL, "numeric"), "1e5")).isEmpty();
        assertThat(codec.read(access(ValueType.DECIMAL, "numeric"), "NaN")).isEmpty();
        assertThat(codec.read(access(ValueType.BOOLEAN, "bool"), "TRUE")).isEmpty();
        assertThat(codec.read(access(ValueType.BOOLEAN, "bool"), "false"))
                .map(BoundValue::value)
                .contains(false);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "tinyint unsigned, 255, 256, -1",
        "smallint unsigned, 65535, 65536, -1",
        "mediumint unsigned, 16777215, 16777216, -1",
        "int unsigned, 4294967295, 4294967296, -1",
        "integer unsigned, 4294967295, 4294967296, -1",
        "INT(10) UNSIGNED, 4294967295, 4294967296, -1",
        "int  unsigned, 4294967295, 4294967296, -1"
    })
    @DisplayName("an unsigned integer type accepts zero up to its maximum and rejects a negative number and one above")
    void readUnsignedIntegers(String typeName, String max, String over, String negative) {
        assertThat(codec.read(access(ValueType.INTEGER, typeName), max))
                .map(BoundValue::value)
                .contains(Long.parseLong(max));
        assertThat(codec.read(access(ValueType.INTEGER, typeName), "0"))
                .map(BoundValue::value)
                .contains(0L);
        assertThat(codec.read(access(ValueType.INTEGER, typeName), over)).isEmpty();
        assertThat(codec.read(access(ValueType.INTEGER, typeName), negative)).isEmpty();
    }

    @Test
    @DisplayName("an unsigned bigint accepts zero up to 2^64-1 and rejects a negative number and one above")
    void readUnsignedBigInteger() {
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint unsigned"), "18446744073709551615"))
                .map(BoundValue::value)
                .contains(new BigDecimal("18446744073709551615"));
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint unsigned"), "0"))
                .map(BoundValue::value)
                .contains(new BigDecimal("0"));
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint unsigned"), "18446744073709551616"))
                .isEmpty();
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint unsigned"), "-1"))
                .isEmpty();
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint(20) unsigned"), "-1"))
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "tinyint, -128, 255, -129, 256",
        "smallint, -32768, 65535, -32769, 65536",
        "mediumint, -8388608, 16777215, -8388609, 16777216",
        "int, -2147483648, 4294967295, -2147483649, 4294967296",
        "int(11), -2147483648, 4294967295, -2147483649, 4294967296"
    })
    @DisplayName(
            "a type name without unsigned keeps the former range: the signed lower bound up to the unsigned maximum")
    void unsignedFreeNamesKeepTheFormerRange(String typeName, String min, String max, String below, String above) {
        assertThat(codec.read(access(ValueType.INTEGER, typeName), min))
                .map(BoundValue::value)
                .contains(Long.parseLong(min));
        assertThat(codec.read(access(ValueType.INTEGER, typeName), max))
                .map(BoundValue::value)
                .contains(Long.parseLong(max));
        assertThat(codec.read(access(ValueType.INTEGER, typeName), below)).isEmpty();
        assertThat(codec.read(access(ValueType.INTEGER, typeName), above)).isEmpty();
        assertThat(codec.read(access(ValueType.BIG_INTEGER, "bigint"), "-9223372036854775808"))
                .isPresent();
    }

    @Test
    @DisplayName("zoned values without an offset are read as UTC, and Z or +09:00 are moved to UTC")
    void readZoned() {
        OffsetDateTime expected = OffsetDateTime.of(2026, 3, 8, 1, 30, 0, 0, ZoneOffset.UTC);
        ColumnAccess zoned = access(ValueType.DATETIME_TZ, "timestamptz");

        assertThat(codec.read(zoned, "2026-03-08T01:30:00"))
                .map(BoundValue::value)
                .contains(expected);
        assertThat(codec.read(zoned, "2026-03-08T01:30:00Z"))
                .map(BoundValue::value)
                .contains(expected);
        assertThat(codec.read(zoned, "2026-03-08T10:30:00+09:00"))
                .map(BoundValue::value)
                .contains(expected);
        assertThat(codec.read(access(ValueType.TIME_TZ, "timetz"), "10:30:00+09:00"))
                .map(BoundValue::value)
                .contains(OffsetTime.of(1, 30, 0, 0, ZoneOffset.UTC));
        assertThat(codec.read(zoned, "2026-02-30T00:00:00")).isEmpty();
        assertThat(codec.read(access(ValueType.DATE, "date"), "2026-1-1")).isEmpty();
        assertThat(codec.read(access(ValueType.TIME, "time"), "25:00")).isEmpty();
        assertThat(codec.read(access(ValueType.STRING, "varchar"), null)).isEmpty();
    }

    @Test
    @DisplayName("each value type uses its own setter and OTHER and zoned datetimes go through the dialect")
    void bindSetters() throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        RowDialect postgres = RowDialect.of(DatabaseProduct.POSTGRESQL);
        RowDialect mysql = RowDialect.of(DatabaseProduct.MYSQL);
        OffsetDateTime utc = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);

        codec.bind(statement, 1, new BoundValue(ValueType.STRING, "'; --"), postgres);
        codec.bind(statement, 2, new BoundValue(ValueType.INTEGER, 5L), postgres);
        codec.bind(statement, 3, new BoundValue(ValueType.DECIMAL, new BigDecimal("1.50")), postgres);
        codec.bind(statement, 4, new BoundValue(ValueType.BOOLEAN, true), postgres);
        codec.bind(statement, 5, new BoundValue(ValueType.DATE, LocalDate.of(2026, 1, 2)), postgres);
        codec.bind(statement, 6, new BoundValue(ValueType.OTHER, "0b8e1c7a-0000-4000-8000-000000000000"), postgres);
        codec.bind(statement, 7, new BoundValue(ValueType.OTHER, "A"), mysql);
        codec.bind(statement, 8, new BoundValue(ValueType.DATETIME_TZ, utc), postgres);
        codec.bind(statement, 9, new BoundValue(ValueType.DATETIME_TZ, utc), mysql);
        codec.bind(statement, 10, new BoundValue(ValueType.INTEGER, null), postgres);

        verify(statement).setString(1, "'; --");
        verify(statement).setLong(2, 5L);
        verify(statement).setBigDecimal(3, new BigDecimal("1.50"));
        verify(statement).setBoolean(4, true);
        verify(statement).setObject(5, LocalDate.of(2026, 1, 2));
        verify(statement).setObject(6, "0b8e1c7a-0000-4000-8000-000000000000", Types.OTHER);
        verify(statement).setString(7, "A");
        verify(statement).setObject(8, utc);
        verify(statement).setObject(9, LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        verify(statement).setNull(10, Types.BIGINT);
    }

    @Test
    @DisplayName(
            "read values are text without losing digits, zoned ones in UTC with +00:00, and the JVM zone does not matter")
    void readCells() throws SQLException {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            ResultSet resultSet = mock(ResultSet.class);
            RowDialect postgres = RowDialect.of(DatabaseProduct.POSTGRESQL);
            RowDialect mysql = RowDialect.of(DatabaseProduct.MYSQL);
            when(resultSet.getBigDecimal(1)).thenReturn(new BigDecimal("1E+3"));
            when(resultSet.getObject(2, OffsetDateTime.class))
                    .thenReturn(OffsetDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9)));
            when(resultSet.getString(3)).thenReturn("2026-01-01 00:00:00.120");
            when(resultSet.getObject(4, OffsetTime.class)).thenReturn(OffsetTime.of(9, 0, 0, 0, ZoneOffset.ofHours(9)));
            when(resultSet.getObject(5, LocalTime.class)).thenReturn(LocalTime.of(7, 5));
            when(resultSet.getBoolean(6)).thenReturn(false);
            when(resultSet.wasNull()).thenReturn(true);

            assertThat(codec.readCell(resultSet, 1, ValueType.DECIMAL, Integer.MAX_VALUE, postgres))
                    .isEqualTo(new CellValue.Text("1000", false));
            assertThat(codec.readCell(resultSet, 2, ValueType.DATETIME_TZ, Integer.MAX_VALUE, postgres))
                    .isEqualTo(new CellValue.Text("2026-01-01T00:00:00+00:00", false));
            assertThat(codec.readCell(resultSet, 3, ValueType.DATETIME_TZ, Integer.MAX_VALUE, mysql))
                    .isEqualTo(new CellValue.Text("2026-01-01T00:00:00.12+00:00", false));
            assertThat(codec.readCell(resultSet, 3, ValueType.DATETIME, Integer.MAX_VALUE, mysql))
                    .isEqualTo(new CellValue.Text("2026-01-01T00:00:00.12", false));
            assertThat(codec.readCell(resultSet, 4, ValueType.TIME_TZ, Integer.MAX_VALUE, postgres))
                    .isEqualTo(new CellValue.Text("00:00:00+00:00", false));
            assertThat(codec.readCell(resultSet, 5, ValueType.TIME, Integer.MAX_VALUE, postgres))
                    .isEqualTo(new CellValue.Text("07:05:00", false));
            assertThat(codec.readCell(resultSet, 6, ValueType.BOOLEAN, Integer.MAX_VALUE, postgres))
                    .as("NULL")
                    .isEqualTo(new CellValue.Text(null, false));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName(
            "MySQL family booleans are read as integers: 0 is false, 1 is true, NULL is NULL, others are unreadable")
    void readBooleanAsInteger() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        RowDialect mysql = RowDialect.of(DatabaseProduct.MYSQL);
        RowDialect mariadb = RowDialect.of(DatabaseProduct.MARIADB);
        when(resultSet.getLong(1)).thenReturn(0L);
        when(resultSet.getLong(2)).thenReturn(1L);
        when(resultSet.getLong(3)).thenReturn(5L);
        when(resultSet.getLong(4)).thenReturn(-1L);
        when(resultSet.getLong(5)).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(false, false, false, false, true);

        assertThat(codec.readCell(resultSet, 1, ValueType.BOOLEAN, Integer.MAX_VALUE, mysql))
                .isEqualTo(new CellValue.Text("false", false));
        assertThat(codec.readCell(resultSet, 2, ValueType.BOOLEAN, Integer.MAX_VALUE, mariadb))
                .isEqualTo(new CellValue.Text("true", false));
        assertThat(codec.readCell(resultSet, 3, ValueType.BOOLEAN, Integer.MAX_VALUE, mysql))
                .isEqualTo(new CellValue.Unreadable("java.lang.IllegalStateException"));
        assertThat(codec.readCell(resultSet, 4, ValueType.BOOLEAN, Integer.MAX_VALUE, mysql))
                .isEqualTo(new CellValue.Unreadable("java.lang.IllegalStateException"));
        assertThat(codec.readCell(resultSet, 5, ValueType.BOOLEAN, Integer.MAX_VALUE, mysql))
                .isEqualTo(new CellValue.Text(null, false));
    }

    @Test
    @DisplayName("long text is cut at the code point limit and marked, exactly at the limit it is kept")
    void truncation() {
        String emoji = "😀";

        assertThat(ColumnValueCodec.truncate(emoji.repeat(100), 100))
                .isEqualTo(new CellValue.Text(emoji.repeat(100), false));
        assertThat(ColumnValueCodec.truncate(emoji.repeat(101), 100))
                .isEqualTo(new CellValue.Text(emoji.repeat(100), true));
        assertThat(ColumnValueCodec.truncate("", 100)).isEqualTo(new CellValue.Text("", false));
        assertThat(ColumnValueCodec.truncate(null, 100)).isEqualTo(new CellValue.Text(null, false));
        assertThat(new CellValue.Text("秘密", false).toString()).doesNotContain("秘密");
    }

    @Test
    @DisplayName("unchecked conversion failures become an unreadable result and never escape")
    void uncheckedFailuresAreResults() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(anyInt(), eq(LocalDate.class)))
                .thenThrow(new java.time.DateTimeException("0000-00-00"));
        when(resultSet.getBigDecimal(anyInt())).thenThrow(new NumberFormatException("NaN"));

        CellValue date = codec.readCell(
                resultSet, 1, ValueType.DATE, Integer.MAX_VALUE, RowDialect.of(DatabaseProduct.POSTGRESQL));
        CellValue decimal = codec.readCell(
                resultSet, 2, ValueType.DECIMAL, Integer.MAX_VALUE, RowDialect.of(DatabaseProduct.MYSQL));

        assertThat(date).isEqualTo(new CellValue.Unreadable("java.time.DateTimeException"));
        assertThat(decimal).isEqualTo(new CellValue.Unreadable("java.lang.NumberFormatException"));
        assertThat(date.toString()).doesNotContain("0000");
        assertThat(codec.read(access(ValueType.DECIMAL, "numeric"), "9".repeat(1000)))
                .isPresent();
        verify(resultSet).getObject(any(Integer.class), eq(LocalDate.class));

        ResultSet text = mock(ResultSet.class);
        when(text.getString(1)).thenReturn("0000-00-00");
        when(text.getString(2)).thenReturn("838:59:59");
        when(text.getString(3)).thenReturn("2026-03-08 02:30:00.123456");
        RowDialect mariadb = RowDialect.of(DatabaseProduct.MARIADB);
        assertThat(codec.readCell(text, 1, ValueType.DATE, Integer.MAX_VALUE, mariadb))
                .as("ゼロ日付は NULL にせず読めない値")
                .isInstanceOf(CellValue.Unreadable.class);
        assertThat(codec.readCell(text, 2, ValueType.TIME, Integer.MAX_VALUE, mariadb))
                .as("24 時間を超える時刻は余りにせず読めない値")
                .isInstanceOf(CellValue.Unreadable.class);
        assertThat(codec.readCell(text, 3, ValueType.DATETIME, Integer.MAX_VALUE, mariadb))
                .isEqualTo(new CellValue.Text("2026-03-08T02:30:00.123456", false));
    }

    @Test
    @DisplayName("fractional digits come from the type name, then the DSL precision, then the database default")
    void fractionDigits() {
        assertThat(ColumnValueCodec.kind(
                                Optional.of(DatabaseProduct.POSTGRESQL),
                                column("t").dbType("timestamptz").build())
                        .fractionDigits())
                .isEqualTo(6);
        assertThat(ColumnValueCodec.kind(
                                Optional.of(DatabaseProduct.MYSQL),
                                column("t").dbType("datetime").build())
                        .fractionDigits())
                .isZero();
        assertThat(ColumnValueCodec.kind(
                                Optional.of(DatabaseProduct.MYSQL),
                                column("t").dbType("datetime(3)").build())
                        .fractionDigits())
                .isEqualTo(3);
        assertThat(ColumnValueCodec.kind(
                                Optional.of(DatabaseProduct.MARIADB),
                                column("t").dbType("timestamp", 6).build())
                        .fractionDigits())
                .isEqualTo(6);
        assertThat(ColumnValueCodec.kind(
                                Optional.empty(), column("t").dbType("time").build())
                        .fractionDigits())
                .as("DB の種類が分からないときは狭める向き")
                .isZero();
        assertThat(ColumnValueCodec.kind(
                                Optional.of(DatabaseProduct.POSTGRESQL),
                                column("d").dbType("date").build())
                        .fractionDigits())
                .isNull();
    }

    @Test
    @DisplayName("typed values are written back as the same text as read values")
    void formatTypedValues() {
        assertThat(ColumnValueCodec.format(new BoundValue(ValueType.INTEGER, 7L)))
                .isEqualTo("7");
        assertThat(ColumnValueCodec.format(new BoundValue(ValueType.DECIMAL, new BigDecimal("1.50"))))
                .isEqualTo("1.50");
        assertThat(ColumnValueCodec.format(new BoundValue(ValueType.BOOLEAN, Boolean.TRUE)))
                .isEqualTo("true");
        assertThat(ColumnValueCodec.format(new BoundValue(ValueType.DATE, LocalDate.of(2026, 1, 2))))
                .isEqualTo("2026-01-02");
        assertThat(ColumnValueCodec.format(new BoundValue(ValueType.DATETIME, LocalDateTime.of(2026, 1, 2, 3, 4, 5))))
                .isEqualTo("2026-01-02T03:04:05");
        assertThat(ColumnValueCodec.format(new BoundValue(
                        ValueType.DATETIME_TZ, OffsetDateTime.of(2026, 1, 2, 12, 0, 0, 0, ZoneOffset.ofHours(9)))))
                .isEqualTo("2026-01-02T03:00:00+00:00");
        assertThat(ColumnValueCodec.format(new BoundValue(ValueType.STRING, null)))
                .isNull();
    }

    @Test
    @DisplayName("write values are bound with typed setters and NULL keeps its SQL type")
    void bindForWrite() throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ColumnValueCodec codec = new ColumnValueCodec();
        RowDialect postgres = RowDialect.of(DatabaseProduct.POSTGRESQL);

        codec.bind(
                statement, 1, new BoundValue(ValueType.BIG_INTEGER, new BigDecimal("9223372036854775807")), postgres);
        codec.bind(
                statement, 2, new BoundValue(ValueType.TIME_TZ, OffsetTime.of(1, 0, 0, 0, ZoneOffset.UTC)), postgres);
        codec.bind(statement, 3, new BoundValue(ValueType.DATETIME_TZ, null), postgres);

        verify(statement).setBigDecimal(1, new BigDecimal("9223372036854775807"));
        verify(statement).setObject(2, OffsetTime.of(1, 0, 0, 0, ZoneOffset.UTC));
        verify(statement).setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
