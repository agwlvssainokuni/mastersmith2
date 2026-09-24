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
package cherry.mastersmith.targetdb.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetTable;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MariaDB の情報スキーマの読み手の結合テスト（共通の確かめは {@link AbstractSchemaQueriesIT}）。 */
class MariadbSchemaQueriesIT extends AbstractSchemaQueriesIT {

    @Override
    DatabaseProduct product() {
        return DatabaseProduct.MARIADB;
    }

    @Override
    String expectedIntegerType() {
        return "int";
    }

    @Override
    String expectedDecimalType() {
        return "decimal";
    }

    @Override
    String expectedAmountDefault() {
        return "0.00";
    }

    @Override
    String expectedStatusDefault() {
        return "'new'";
    }

    @Test
    @DisplayName("sequences are excluded and a system-versioned table keeps only its readable primary key column")
    void mariadbSpecificObjects() throws SQLException {
        TargetSchema read = readExtraSchema(
                "CREATE TABLE %s.base (id INT PRIMARY KEY)",
                "CREATE SEQUENCE %s.base_seq",
                "CREATE TABLE %s.history (id INT PRIMARY KEY, v VARCHAR(10)) WITH SYSTEM VERSIONING");

        assertThat(read.tables()).extracting(TargetTable::name).containsExactlyInAnyOrder("base", "history");
        assertThat(read.table("history").orElseThrow().view()).isFalse();
        assertThat(read.table("history").orElseThrow().columns())
                .extracting(TargetColumn::name)
                .as("隠れたカラム row_start・row_end は一覧に出ない")
                .containsExactly("id", "v");
        assertThat(read.table("history").orElseThrow().primaryKey())
                .as("主キーの隠れたカラム row_end は除く")
                .containsExactly("id");
    }
}
