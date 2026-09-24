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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ListFormat;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dslmanage.generate.TypeCategoryMapping.Defaults;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 型の分類と初期値の表の単体テスト（BR2.2〜BR2.5、AC1.2.2、functional-spec.md の 2節）。 */
class TypeCategoryMappingTest {

    private static TypeCategory categorize(String name, Long length, Integer precision, String columnType) {
        return TypeCategoryMapping.categorize(new TargetDbType(name, length, precision, null, columnType));
    }

    @ParameterizedTest(name = "{0} length={1} precision={2} columnType={3} -> {4}")
    @CsvSource(
            nullValues = "null",
            value = {
                // MySQL・MariaDB（情報スキーマの DATA_TYPE と COLUMN_TYPE）
                "varchar, 40, null, varchar(40), SHORT_TEXT",
                "char, 10, null, char(10), SHORT_TEXT",
                "text, 65535, null, text, LONG_TEXT",
                "tinytext, 255, null, tinytext, LONG_TEXT",
                "mediumtext, 16777215, null, mediumtext, LONG_TEXT",
                "longtext, 4294967295, null, longtext, LONG_TEXT",
                "int, null, 10, int, NUMBER",
                "bigint, null, 19, bigint unsigned, NUMBER",
                "decimal, null, 10, 'decimal(10,2)', NUMBER",
                "double, null, 22, double, NUMBER",
                "tinyint, null, 3, tinyint(1), BOOLEAN",
                "tinyint, null, 3, tinyint(1) unsigned, BOOLEAN",
                "tinyint, null, 3, tinyint, NUMBER",
                "tinyint, null, 3, tinyint(4), NUMBER",
                "tinyint, null, 3, tinyint unsigned, NUMBER",
                "tinyint, null, 3, null, NUMBER",
                "bit, null, 1, bit(1), BOOLEAN",
                "bit, null, 8, bit(8), UNSUPPORTED",
                "date, null, null, date, DATE",
                "datetime, null, null, datetime(6), DATETIME",
                "timestamp, null, null, timestamp, DATETIME",
                "time, null, null, time, TIME",
                "json, null, null, json, UNSUPPORTED",
                "enum, 1, null, 'enum(''a'')', UNSUPPORTED",
                "year, null, null, year, UNSUPPORTED",
                // PostgreSQL（udt_name。型の全体の表記は無い）
                "varchar, 255, null, null, SHORT_TEXT",
                "varchar, null, null, null, LONG_TEXT",
                "bpchar, 1, null, null, SHORT_TEXT",
                "int2, null, 16, null, NUMBER",
                "int4, null, 32, null, NUMBER",
                "int8, null, 64, null, NUMBER",
                "numeric, null, 10, null, NUMBER",
                "float4, null, 24, null, NUMBER",
                "float8, null, 53, null, NUMBER",
                "bool, null, null, null, BOOLEAN",
                "bit, 1, null, null, UNSUPPORTED",
                "timestamptz, null, null, null, DATETIME",
                "timetz, null, null, null, TIME",
                "uuid, null, null, null, UNSUPPORTED",
                "bytea, null, null, null, UNSUPPORTED",
                "jsonb, null, null, null, UNSUPPORTED",
            })
    @DisplayName("type names of the three databases are mapped to their category")
    void categoriesOfTheThreeDatabases(
            String name, Long length, Integer precision, String columnType, TypeCategory expected) {
        assertThat(categorize(name, length, precision, columnType)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a string of length 255 is short text and one of length 256 is long text")
    void boundaryOfTheLength() {
        assertThat(categorize("varchar", 255L, null, null)).isEqualTo(TypeCategory.SHORT_TEXT);
        assertThat(categorize("varchar", 256L, null, null)).isEqualTo(TypeCategory.LONG_TEXT);
        assertThat(categorize("char", 0L, null, null)).isEqualTo(TypeCategory.SHORT_TEXT);
    }

    @Test
    @DisplayName("names are compared without regard to upper and lower case")
    void caseInsensitive() {
        assertThat(categorize("VARCHAR", 10L, null, null)).isEqualTo(TypeCategory.SHORT_TEXT);
        assertThat(categorize("Int4", null, 32, null)).isEqualTo(TypeCategory.NUMBER);
        assertThat(categorize("TINYINT", null, 3, "TINYINT(1)")).isEqualTo(TypeCategory.BOOLEAN);
        assertThat(categorize("BOOLEAN", null, null, null)).isEqualTo(TypeCategory.BOOLEAN);
        assertThat(categorize("TimeStampTz", null, null, null)).isEqualTo(TypeCategory.DATETIME);
    }

    @Test
    @DisplayName("tinyint(1) is told from other tinyint only by the full column type")
    void tinyintOneNeedsTheFullColumnType() {
        assertThat(categorize("tinyint", null, 3, " tinyint(1) ")).isEqualTo(TypeCategory.BOOLEAN);
        assertThat(categorize("tinyint", null, 3, "tinyint(10)")).isEqualTo(TypeCategory.NUMBER);
        assertThat(categorize("smallint", null, 5, "tinyint(1)"))
                .as("表記が tinyint(1) でも型の名前が違えば真偽値にしない")
                .isEqualTo(TypeCategory.NUMBER);
        assertThat(categorize("bit", null, null, null)).isEqualTo(TypeCategory.UNSUPPORTED);
    }

    @Test
    @DisplayName("unknown type names are unsupported")
    void unknownTypes() {
        assertThat(categorize("geometry", null, null, null)).isEqualTo(TypeCategory.UNSUPPORTED);
        assertThat(categorize("my_domain", null, null, null)).isEqualTo(TypeCategory.UNSUPPORTED);
        assertThat(categorize("interval", null, null, null)).isEqualTo(TypeCategory.UNSUPPORTED);
    }

    @Test
    @DisplayName("each category has the form part, search, list, detail and format of the table in the spec")
    void defaultsOfEachCategory() {
        assertThat(TypeCategoryMapping.defaults(TypeCategory.SHORT_TEXT))
                .isEqualTo(new Defaults(FormPart.TEXT, SearchOperator.CONTAINS, true, true, null));
        assertThat(TypeCategoryMapping.defaults(TypeCategory.LONG_TEXT))
                .isEqualTo(new Defaults(FormPart.TEXTAREA, SearchOperator.CONTAINS, false, true, null));
        assertThat(TypeCategoryMapping.defaults(TypeCategory.NUMBER))
                .isEqualTo(new Defaults(FormPart.NUMBER, SearchOperator.RANGE, true, true, ListFormat.NUMBER_GROUPED));
        assertThat(TypeCategoryMapping.defaults(TypeCategory.BOOLEAN))
                .isEqualTo(
                        new Defaults(FormPart.CHECKBOX, SearchOperator.CHOICE, true, true, ListFormat.BOOLEAN_YES_NO));
        assertThat(TypeCategoryMapping.defaults(TypeCategory.DATE))
                .isEqualTo(new Defaults(FormPart.DATE, SearchOperator.RANGE, true, true, ListFormat.DATE));
        assertThat(TypeCategoryMapping.defaults(TypeCategory.DATETIME))
                .isEqualTo(new Defaults(FormPart.DATETIME, SearchOperator.RANGE, true, true, ListFormat.DATETIME));
        assertThat(TypeCategoryMapping.defaults(TypeCategory.TIME))
                .isEqualTo(new Defaults(FormPart.TEXT, SearchOperator.EQUALS, true, true, ListFormat.TIME));
        assertThat(TypeCategoryMapping.defaults(TypeCategory.UNSUPPORTED))
                .isEqualTo(new Defaults(FormPart.TEXT, null, false, false, null));
        for (TypeCategory category : TypeCategory.values()) {
            assertThat(TypeCategoryMapping.defaults(category))
                    .as(category.name())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("only short and long text count as strings and the defaults require a category and a form part")
    void textAndRequiredValues() {
        assertThat(TypeCategory.values())
                .filteredOn(TypeCategoryMapping::isText)
                .containsExactly(TypeCategory.SHORT_TEXT, TypeCategory.LONG_TEXT);
        assertThatThrownBy(() -> TypeCategoryMapping.defaults(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Defaults(null, null, false, false, null)).isInstanceOf(NullPointerException.class);
    }
}
