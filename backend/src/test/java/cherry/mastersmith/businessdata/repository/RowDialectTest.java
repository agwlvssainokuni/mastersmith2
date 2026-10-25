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
        assertThat(RowDialect.baseName("timestamp(3) with time zone")).isEqualTo("timestamp");
        assertThat(POSTGRES.castToText("\"a\"")).isEqualTo("CAST(\"a\" AS TEXT)");
        assertThat(MYSQL.readsTemporalAsText()).isTrue();
        assertThat(MARIADB.readsTemporalAsText()).isTrue();
        assertThat(POSTGRES.readsTemporalAsText()).isFalse();
    }
}
