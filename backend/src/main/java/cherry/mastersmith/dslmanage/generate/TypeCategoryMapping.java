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
package cherry.mastersmith.dslmanage.generate;

import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ListFormat;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 型の名前から分類への対応と、分類からの初期値の表（BR2.2〜BR2.5、functional-spec.md の 2節）。
 *
 * <p>型の名前は U1 の写しが返すもの（MySQL・MariaDB は情報スキーマの {@code DATA_TYPE}、PostgreSQL は {@code udt_name}）で、
 * 大文字・小文字を区別せずに比べる。表に無い型は {@link TypeCategory#UNSUPPORTED} にする。
 */
public final class TypeCategoryMapping {

    /** 短い文字列の長さの上限（この長さまでが {@link TypeCategory#SHORT_TEXT}）。 */
    public static final long SHORT_TEXT_MAX_LENGTH = 255;

    /** 長さで短い・長いを分ける文字列の型（MySQL・MariaDB の char・varchar、PostgreSQL の bpchar・varchar）。 */
    private static final Set<String> SIZED_TEXT_TYPES = Set.of("char", "varchar", "nchar", "nvarchar", "bpchar");

    /** 長さによらず長い文字列の型。 */
    private static final Set<String> LONG_TEXT_TYPES = Set.of("tinytext", "text", "mediumtext", "longtext", "clob");

    /** 整数・小数の型。 */
    private static final Set<String> NUMBER_TYPES = Set.of(
            "tinyint",
            "smallint",
            "mediumint",
            "int",
            "integer",
            "bigint",
            "decimal",
            "numeric",
            "float",
            "double",
            "real",
            "int2",
            "int4",
            "int8",
            "float4",
            "float8");

    /** 真偽値の型。 */
    private static final Set<String> BOOLEAN_TYPES = Set.of("boolean", "bool");

    /** 日時の型（時差つきを含む）。 */
    private static final Set<String> DATETIME_TYPES = Set.of("datetime", "timestamp", "timestamptz");

    /** 時刻の型（時差つきを含む）。 */
    private static final Set<String> TIME_TYPES = Set.of("time", "timetz");

    /** MySQL・MariaDB の {@code tinyint(1)}（{@code unsigned} などの語が続くものを含む）。 */
    private static final Pattern TINYINT_1 = Pattern.compile("tinyint\\(1\\)(\\s.*)?", Pattern.CASE_INSENSITIVE);

    private static final String BIT = "bit";

    private static final String DATE = "date";

    private static final String TINYINT = "tinyint";

    /** 分類ごとの初期値（functional-spec.md の 2節の表）。 */
    private static final Map<TypeCategory, Defaults> DEFAULTS = Map.of(
            TypeCategory.SHORT_TEXT,
            new Defaults(FormPart.TEXT, SearchOperator.CONTAINS, true, true, null),
            TypeCategory.LONG_TEXT,
            new Defaults(FormPart.TEXTAREA, SearchOperator.CONTAINS, false, true, null),
            TypeCategory.NUMBER,
            new Defaults(FormPart.NUMBER, SearchOperator.RANGE, true, true, ListFormat.NUMBER_GROUPED),
            TypeCategory.BOOLEAN,
            new Defaults(FormPart.CHECKBOX, SearchOperator.CHOICE, true, true, ListFormat.BOOLEAN_YES_NO),
            TypeCategory.DATE,
            new Defaults(FormPart.DATE, SearchOperator.RANGE, true, true, ListFormat.DATE),
            TypeCategory.DATETIME,
            new Defaults(FormPart.DATETIME, SearchOperator.RANGE, true, true, ListFormat.DATETIME),
            TypeCategory.TIME,
            new Defaults(FormPart.TEXT, SearchOperator.EQUALS, true, true, ListFormat.TIME),
            TypeCategory.UNSUPPORTED,
            new Defaults(FormPart.TEXT, null, false, false, null));

    private TypeCategoryMapping() {}

    /**
     * 分類ごとの初期値。
     *
     * @param formPart フォーム部品（BR2.3）
     * @param searchOperator 検索の演算子（null は検索しない。BR2.4）
     * @param listed 一覧に表示し、並べ替えできるなら true（BR2.5）
     * @param detailVisible 詳細に表示するなら true（BR2.5）
     * @param format 一覧の書式（無ければ null。BR2.5）
     */
    public record Defaults(
            FormPart formPart,
            SearchOperator searchOperator,
            boolean listed,
            boolean detailVisible,
            ListFormat format) {

        /** フォーム部品が必須であることを確かめる。 */
        public Defaults {
            Objects.requireNonNull(formPart, "formPart は必須です");
        }
    }

    /**
     * 型を分類する（BR2.2）。
     *
     * @param type U1 の写しの型
     * @return 分類
     */
    public static TypeCategory categorize(TargetDbType type) {
        String name = type.typeName().toLowerCase(Locale.ROOT);
        if (SIZED_TEXT_TYPES.contains(name)) {
            Long length = type.length();
            return length != null && length <= SHORT_TEXT_MAX_LENGTH ? TypeCategory.SHORT_TEXT : TypeCategory.LONG_TEXT;
        }
        if (LONG_TEXT_TYPES.contains(name)) {
            return TypeCategory.LONG_TEXT;
        }
        if (BOOLEAN_TYPES.contains(name) || isTinyint1(name, type) || isBit1(name, type)) {
            return TypeCategory.BOOLEAN;
        }
        if (NUMBER_TYPES.contains(name)) {
            return TypeCategory.NUMBER;
        }
        if (DATE.equals(name)) {
            return TypeCategory.DATE;
        }
        if (DATETIME_TYPES.contains(name)) {
            return TypeCategory.DATETIME;
        }
        if (TIME_TYPES.contains(name)) {
            return TypeCategory.TIME;
        }
        return TypeCategory.UNSUPPORTED;
    }

    /**
     * 文字列の型か（BR4.1 の「文字列で長さがあれば maxLength」の文字列）。
     *
     * @param category 分類
     * @return 短い・長い文字列なら true
     */
    public static boolean isText(TypeCategory category) {
        return category == TypeCategory.SHORT_TEXT || category == TypeCategory.LONG_TEXT;
    }

    /**
     * 分類ごとの初期値を返す（BR2.3〜BR2.5）。
     *
     * @param category 分類
     * @return 初期値
     */
    public static Defaults defaults(TypeCategory category) {
        return DEFAULTS.get(Objects.requireNonNull(category, "category は必須です"));
    }

    private static boolean isTinyint1(String name, TargetDbType type) {
        return TINYINT.equals(name)
                && type.columnType() != null
                && TINYINT_1.matcher(type.columnType().strip()).matches();
    }

    private static boolean isBit1(String name, TargetDbType type) {
        return BIT.equals(name) && Integer.valueOf(1).equals(type.precision());
    }
}
