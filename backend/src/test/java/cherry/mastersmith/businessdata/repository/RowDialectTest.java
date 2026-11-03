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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 行の SQL の方言（BR5.5・BR7.3・BR7.7・BR7.9、performance-design.md の 1節）の単体テスト。 */
class RowDialectTest {

    private static final RowDialect POSTGRES = RowDialect.of(DatabaseProduct.POSTGRESQL);

    private static final RowDialect MYSQL = RowDialect.of(DatabaseProduct.MYSQL);

    private static final RowDialect MARIADB = RowDialect.of(DatabaseProduct.MARIADB);

    @Test
    @DisplayName("null values come last in ascending and first in descending order on every database")
    void nullOrdering() {
        assertThat(POSTGRES.orderTerm("\"a\"", SortDirection.ASC)).isEqualTo("\"a\" ASC NULLS LAST");
        assertThat(POSTGRES.orderTerm("\"a\"", SortDirection.DESC)).isEqualTo("\"a\" DESC NULLS FIRST");
        assertThat(MYSQL.orderTerm("`a`", SortDirection.ASC)).isEqualTo("`a` IS NULL ASC, `a` ASC");
        assertThat(MARIADB.orderTerm("`a`", SortDirection.DESC)).isEqualTo("`a` IS NULL DESC, `a` DESC");
    }

    @Test
    @DisplayName("strings and OTHER values are read through SUBSTRING with one more code point than the limit")
    void selectExpressions() {
        assertThat(POSTGRES.selectExpression("\"a\"", access(ValueType.STRING, "x"), 100))
                .isEqualTo("SUBSTRING(\"a\", 1, 101)");
        assertThat(POSTGRES.selectExpression("\"a\"", access(ValueType.OTHER, "x"), 10_000))
                .isEqualTo("SUBSTRING(CAST(\"a\" AS TEXT), 1, 10001)");
        assertThat(MYSQL.selectExpression("`a`", access(ValueType.OTHER, "x"), 100))
                .isEqualTo("SUBSTRING(CAST(`a` AS CHAR), 1, 101)");
        assertThat(MYSQL.selectExpression("`a`", access(ValueType.INTEGER, "x"), 100))
                .isEqualTo("`a`");
    }

    @Test
    @DisplayName("key values are never cut and OTHER keys are read as text")
    void keyExpressions() {
        assertThat(POSTGRES.keyExpression("\"k\"", access(ValueType.STRING, "x")))
                .isEqualTo("\"k\"");
        assertThat(POSTGRES.keyExpression("\"k\"", access(ValueType.OTHER, "x")))
                .isEqualTo("CAST(\"k\" AS TEXT)");
    }

    @Test
    @DisplayName("PostgreSQL compares timetz at UTC and MySQL compares the column as it is")
    void comparisonTarget() {
        assertThat(POSTGRES.comparisonTarget("\"t\"", ValueType.TIME_TZ)).isEqualTo("(\"t\" AT TIME ZONE 'UTC')");
        assertThat(POSTGRES.comparisonTarget("\"t\"", ValueType.DATETIME_TZ)).isEqualTo("\"t\"");
        assertThat(MYSQL.comparisonTarget("`t`", ValueType.TIME_TZ)).isEqualTo("`t`");
    }

    @Test
    @DisplayName(
            "a create without values uses the default form of each database and only PostgreSQL reads keys by name")
    void writeForms() {
        assertThat(POSTGRES.insertWithoutValues("\"s\".\"t\"")).isEqualTo("INSERT INTO \"s\".\"t\" DEFAULT VALUES");
        assertThat(MYSQL.insertWithoutValues("`s`.`t`")).isEqualTo("INSERT INTO `s`.`t` () VALUES ()");
        assertThat(MARIADB.insertWithoutValues("`s`.`t`")).isEqualTo("INSERT INTO `s`.`t` () VALUES ()");
        assertThat(POSTGRES.readsGeneratedKeysByName()).isTrue();
        assertThat(MYSQL.readsGeneratedKeysByName()).isFalse();
        assertThat(MARIADB.readsGeneratedKeysByName()).isFalse();
        assertThat(MARIADB.product()).isEqualTo(DatabaseProduct.MARIADB);
    }

    @Test
    @DisplayName("the BR7.9 type lists are kept per database and the union applies when the database is unknown")
    void equalityLists() {
        assertThat(POSTGRES.equalsSupported("JSON")).isFalse();
        assertThat(POSTGRES.equalsSupported("jsonb")).isTrue();
        assertThat(POSTGRES.equalsSupported("polygon")).isFalse();
        assertThat(MYSQL.equalsSupported("json")).isFalse();
        assertThat(MARIADB.equalsSupported("json")).isTrue();
        assertThat(RowDialect.equalsSupported(Optional.empty(), "json")).isFalse();
        assertThat(RowDialect.equalsSupported(Optional.empty(), "uuid")).isTrue();
        assertThat(RowDialect.equalsSupported(Optional.of(DatabaseProduct.MARIADB), "json"))
                .isTrue();
    }

    @Test
    @DisplayName("zoned datetimes are read in UTC per database")
    void readZoned() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(1, OffsetDateTime.class))
                .thenReturn(OffsetDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9)));
        when(resultSet.getString(1)).thenReturn("2026-01-01 00:00:00");

        assertThat(POSTGRES.readDateTimeTz(resultSet, 1))
                .isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(MYSQL.readDateTimeTz(resultSet, 1))
                .isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(MYSQL.readDateTimeTz(mock(ResultSet.class), 1)).isNull();
        assertThat(POSTGRES.readDateTimeTz(mock(ResultSet.class), 1)).isNull();
    }

    @Test
    @DisplayName("the base name drops the length part and the case")
    void baseName() {
        assertThat(RowDialect.baseName(" VARCHAR(100) ")).isEqualTo("varchar");
        assertThat(RowDialect.baseName("int unsigned")).isEqualTo("int unsigned");
        // 括弧の後ろに unsigned が付く手書きの表記も、符号なしの印を落とさない（FR3.1）。括弧の後ろのほかの語は従来どおり落とす。
        assertThat(RowDialect.baseName("int(10) unsigned")).isEqualTo("int unsigned");
        assertThat(RowDialect.baseName("INT ( 10 )  UNSIGNED")).isEqualTo("int unsigned");
        assertThat(RowDialect.baseName("bigint(20) unsigned zerofill")).isEqualTo("bigint unsigned");
        assertThat(RowDialect.baseName("double(10,2) unsigned")).as("整数の型だけ").isEqualTo("double");
        assertThat(RowDialect.baseName("int(10)")).isEqualTo("int");
        // 桁つきの時差つきの型は時差の有無を残す（BR3.7。以前は "timestamp" になり、時差を持たない日時と読んでいた）。
        assertThat(RowDialect.baseName("timestamp(3) with time zone")).isEqualTo("timestamp with time zone");
        assertThat(RowDialect.baseName("time(3) with time zone")).isEqualTo("time with time zone");
        assertThat(RowDialect.baseName("timestamp(3) without time zone")).isEqualTo("timestamp without time zone");
        assertThat(RowDialect.baseName("timestamptz(3)")).isEqualTo("timestamptz");
        assertThat(RowDialect.baseName("timetz(3)")).isEqualTo("timetz");
        assertThat(RowDialect.baseName("datetime(6)")).isEqualTo("datetime");
        assertThat(POSTGRES.castToText("\"a\"")).isEqualTo("CAST(\"a\" AS TEXT)");
        assertThat(MYSQL.readsTemporalAsText()).isTrue();
        assertThat(MARIADB.readsTemporalAsText()).isTrue();
        assertThat(POSTGRES.readsTemporalAsText()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(DatabaseProduct.class)
    @DisplayName("the length condition counts characters with CHAR_LENGTH on every database")
    void lengthWithin(DatabaseProduct product) {
        assertThat(RowDialect.of(product).lengthWithin("t.\"v\"", 10_000)).isEqualTo("CHAR_LENGTH(t.\"v\") <= 10000");
    }

    @Test
    @DisplayName("the sort expression takes the first 100 characters of text and keeps other types as they are")
    void sortExpression() {
        assertThat(POSTGRES.sortExpression(
                        "\"v\"",
                        cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.access(
                                cherry.mastersmith.businessdata.domain.ValueType.STRING, "varchar")))
                .isEqualTo("SUBSTRING(\"v\", 1, 100)");
        assertThat(MYSQL.sortExpression(
                        "`v`",
                        cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.access(
                                cherry.mastersmith.businessdata.domain.ValueType.STRING, "varchar")))
                .isEqualTo("SUBSTRING(`v`, 1, 100)");
        assertThat(POSTGRES.sortExpression(
                        "\"v\"",
                        cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.access(
                                cherry.mastersmith.businessdata.domain.ValueType.OTHER, "uuid")))
                .isEqualTo("SUBSTRING(CAST(\"v\" AS TEXT), 1, 100)");
        assertThat(MARIADB.sortExpression(
                        "`v`",
                        cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.access(
                                cherry.mastersmith.businessdata.domain.ValueType.OTHER, "json")))
                .isEqualTo("SUBSTRING(" + MARIADB.castToText("`v`") + ", 1, 100)");
        for (cherry.mastersmith.businessdata.domain.ValueType type : List.of(
                cherry.mastersmith.businessdata.domain.ValueType.INTEGER,
                cherry.mastersmith.businessdata.domain.ValueType.BIG_INTEGER,
                cherry.mastersmith.businessdata.domain.ValueType.DECIMAL,
                cherry.mastersmith.businessdata.domain.ValueType.BOOLEAN,
                cherry.mastersmith.businessdata.domain.ValueType.DATE,
                cherry.mastersmith.businessdata.domain.ValueType.DATETIME,
                cherry.mastersmith.businessdata.domain.ValueType.DATETIME_TZ,
                cherry.mastersmith.businessdata.domain.ValueType.TIME,
                cherry.mastersmith.businessdata.domain.ValueType.TIME_TZ)) {
            for (RowDialect dialect : List.of(POSTGRES, MYSQL, MARIADB)) {
                assertThat(dialect.sortExpression(
                                "c",
                                cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.access(type, "x")))
                        .as(type + " はカラムのまま")
                        .isEqualTo("c");
            }
        }
    }

    private static java.sql.ResultSetMetaData meta(int precision, int scale) throws SQLException {
        java.sql.ResultSetMetaData metaData = mock(java.sql.ResultSetMetaData.class);
        when(metaData.getPrecision(1)).thenReturn(precision);
        when(metaData.getScale(1)).thenReturn(scale);
        return metaData;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "DATETIME, 19, 0",
        "DATETIME, 23, 3",
        "DATETIME, 26, 6",
        "TIME, 10, 0",
        "TIME, 14, 3",
        "TIME, 17, 6"
    })
    @DisplayName("MySQL derives the fraction digits from getPrecision")
    void mysqlDigitsFromPrecision(ValueType type, int precision, int expected) throws SQLException {
        assertThat(MYSQL.fractionDigits(meta(precision, 0), 1, type)).isEqualTo(expected);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0", "3", "6"})
    @DisplayName("MariaDB and PostgreSQL read the fraction digits from getScale")
    void scaleDigits(int scale) throws SQLException {
        assertThat(MARIADB.fractionDigits(meta(26, scale), 1, ValueType.DATETIME))
                .isEqualTo(scale);
        assertThat(POSTGRES.fractionDigits(meta(0, scale), 1, ValueType.TIME_TZ))
                .isEqualTo(scale);
    }

    @Test
    @DisplayName("the digits are null for non temporal types, a null metadata and a metadata that throws")
    void digitsAreNullWhenUnknown() throws SQLException {
        assertThat(POSTGRES.fractionDigits(meta(0, 3), 1, ValueType.INTEGER)).isNull();
        assertThat(MYSQL.fractionDigits(null, 1, ValueType.DATETIME)).isNull();
        java.sql.ResultSetMetaData broken = mock(java.sql.ResultSetMetaData.class);
        when(broken.getScale(1)).thenThrow(new SQLException("boom"));
        when(broken.getPrecision(1)).thenThrow(new SQLException("boom"));
        assertThat(POSTGRES.fractionDigits(broken, 1, ValueType.DATETIME)).isNull();
        assertThat(MYSQL.fractionDigits(broken, 1, ValueType.DATETIME)).isNull();
        assertThat(MARIADB.fractionDigits(broken, 1, ValueType.TIME)).isNull();
    }

    @Test
    @DisplayName("only PostgreSQL moves a timetz value to UTC for the comparison and nothing else changes")
    void comparisonValue() {
        var tokyo = java.time.OffsetTime.of(10, 30, 0, 0, ZoneOffset.ofHours(9));
        var bound = new cherry.mastersmith.businessdata.domain.BoundValue(ValueType.TIME_TZ, tokyo);
        var text = new cherry.mastersmith.businessdata.domain.BoundValue(ValueType.STRING, "x");

        assertThat(POSTGRES.comparisonValue(bound).value())
                .isEqualTo(java.time.OffsetTime.of(1, 30, 0, 0, ZoneOffset.UTC));
        assertThat(POSTGRES.comparisonValue(text)).isSameAs(text);
        assertThat(MYSQL.comparisonValue(bound)).isSameAs(bound);
        assertThat(MARIADB.comparisonValue(bound)).isSameAs(bound);
        assertThat(POSTGRES.comparisonTarget("c", ValueType.TIME_TZ)).isEqualTo("(c AT TIME ZONE 'UTC')");
    }

    @Test
    @DisplayName("sentinels are only recognized by PostgreSQL")
    void sentinels() {
        assertThat(POSTGRES.isUnreadableSentinel(java.time.LocalDateTime.MAX)).isTrue();
        assertThat(POSTGRES.isUnreadableSentinel(java.time.LocalDateTime.MIN)).isTrue();
        assertThat(POSTGRES.isUnreadableSentinel(OffsetDateTime.MAX)).isTrue();
        assertThat(POSTGRES.isUnreadableSentinel(java.time.LocalDate.MAX)).isTrue();
        assertThat(POSTGRES.isUnreadableSentinel(java.time.LocalTime.MAX)).isTrue();
        assertThat(POSTGRES.isUnreadableSentinel(java.time.LocalTime.of(23, 59, 59, 999_999_000)))
                .isFalse();
        assertThat(POSTGRES.isUnreadableSentinel(java.time.LocalDateTime.of(2026, 12, 31, 23, 59, 59)))
                .isFalse();
        assertThat(POSTGRES.isUnreadableSentinel("x")).isFalse();
        assertThat(MYSQL.isUnreadableSentinel(java.time.LocalDateTime.MAX)).isFalse();
    }
}
