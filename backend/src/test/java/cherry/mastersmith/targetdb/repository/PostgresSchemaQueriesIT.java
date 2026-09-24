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
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetTable;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PostgreSQL の情報スキーマの読み手の結合テスト（共通の確かめは {@link AbstractSchemaQueriesIT}）。 */
class PostgresSchemaQueriesIT extends AbstractSchemaQueriesIT {

    @Override
    DatabaseProduct product() {
        return DatabaseProduct.POSTGRESQL;
    }

    @Override
    String expectedIntegerType() {
        return "int4";
    }

    @Override
    String expectedDecimalType() {
        return "numeric";
    }

    @Override
    String expectedAmountDefault() {
        return "0";
    }

    @Override
    String expectedStatusDefault() {
        return "'new'::character varying";
    }

    @Test
    @DisplayName("materialized views, sequences and partitions are excluded and a partitioned parent is one table")
    void postgresSpecificObjects() throws SQLException {
        TargetSchema read = readExtraSchema(
                "CREATE TABLE %s.base (id INTEGER PRIMARY KEY)",
                "CREATE MATERIALIZED VIEW %s.base_mv AS SELECT id FROM %s.base",
                "CREATE SEQUENCE %s.base_seq",
                "CREATE TABLE %s.parted (id INTEGER NOT NULL, k INTEGER NOT NULL, PRIMARY KEY (id, k))"
                        + " PARTITION BY LIST (k)",
                "CREATE TABLE %s.parted_1 PARTITION OF %s.parted FOR VALUES IN (1)",
                "CREATE TABLE %s.parted_2 PARTITION OF %s.parted FOR VALUES IN (2)",
                "CREATE TABLE %s.child (id INTEGER PRIMARY KEY, pid INTEGER, pk INTEGER,"
                        + " CONSTRAINT fk_child_parted FOREIGN KEY (pid, pk) REFERENCES %s.parted (id, k))");

        assertThat(read.tables()).extracting(TargetTable::name).containsExactlyInAnyOrder("base", "parted", "child");
        assertThat(read.table("parted").orElseThrow().primaryKey()).containsExactly("id", "k");
        assertThat(read.table("child").orElseThrow().foreignKeys())
                .as("パーティションの子への制約は1つにまとまる")
                .singleElement()
                .satisfies(fk -> {
                    assertThat(fk.referencedTable()).isEqualTo("parted");
                    assertThat(fk.columns()).containsExactly("pid", "pk");
                    assertThat(fk.referencedColumns()).containsExactly("id", "k");
                });
    }
}
