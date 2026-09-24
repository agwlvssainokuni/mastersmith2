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
package cherry.mastersmith.targetdb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** スキーマの写しの値の単体テスト（BR2.3・BR2.5、entities.md の制約）。 */
class TargetSchemaTest {

    private static final TargetDbType INT = new TargetDbType("int4", null, 32, 0);

    private static final TargetDbType VARCHAR = new TargetDbType("varchar", 40L, null, null);

    private static TargetColumn column(String name) {
        return new TargetColumn(name, INT, false, null, null);
    }

    private static TargetTable table(String name, String... columns) {
        List<TargetColumn> list = new ArrayList<>();
        for (String column : columns) {
            list.add(column(column));
        }
        return new TargetTable(name, false, null, list, List.of(), List.of());
    }

    @Test
    @DisplayName("a schema keeps its tables, columns and keys as given and exposes them read-only")
    void keepsValuesAndIsImmutable() {
        List<TargetColumn> columns = new ArrayList<>(
                List.of(column("Id"), new TargetColumn("Parent_Id", INT, true, "0", "親の ID"), column("名前")));
        List<String> primaryKey = new ArrayList<>(List.of("Id"));
        List<TargetForeignKey> foreignKeys = new ArrayList<>(
                List.of(new TargetForeignKey("fk_parent", List.of("Parent_Id"), "Parent", List.of("Id"))));
        TargetTable child = new TargetTable("Child", false, "子の表", columns, primaryKey, foreignKeys);
        List<TargetTable> tables = new ArrayList<>(List.of(child, table("Parent", "Id")));
        TargetSchema schema = new TargetSchema(DatabaseProduct.POSTGRESQL, "App_Schema", tables);

        columns.clear();
        primaryKey.clear();
        foreignKeys.clear();
        tables.clear();

        assertThat(schema.schemaName()).isEqualTo("App_Schema");
        assertThat(schema.tables()).extracting(TargetTable::name).containsExactly("Child", "Parent");
        TargetTable read = schema.table("Child").orElseThrow();
        assertThat(read.columns()).extracting(TargetColumn::name).containsExactly("Id", "Parent_Id", "名前");
        assertThat(read.primaryKey()).containsExactly("Id");
        assertThat(read.foreignKeys()).singleElement().satisfies(fk -> {
            assertThat(fk.referencedTable()).isEqualTo("Parent");
            assertThat(fk.referencedColumns()).containsExactly("Id");
        });
        assertThat(read.columns().get(1).defaultValue()).isEqualTo("0");
        assertThat(schema.table("child")).as("名前は大文字・小文字を区別する").isEmpty();
        assertThatThrownBy(() -> schema.tables().add(table("X", "a")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> read.columns().add(column("x"))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> read.primaryKey().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n", "　"})
    @DisplayName("an empty or blank comment is treated as no comment on tables and columns")
    void blankCommentIsNoComment(String comment) {
        TargetColumn column = new TargetColumn("a", VARCHAR, true, null, comment);
        TargetTable table = new TargetTable("t", false, comment, List.of(column), List.of(), List.of());

        assertThat(column.comment()).isNull();
        assertThat(table.comment()).isNull();
    }

    @Test
    @DisplayName("a non-blank comment is kept as it is including surrounding spaces")
    void commentIsKept() {
        assertThat(new TargetColumn("a", VARCHAR, true, null, " 顧客の名前 ").comment())
                .isEqualTo(" 顧客の名前 ");
    }

    @Test
    @DisplayName("duplicate table names and duplicate column names are rejected")
    void duplicatesAreRejected() {
        assertThatThrownBy(
                        () -> new TargetSchema(DatabaseProduct.MYSQL, "s", List.of(table("t", "a"), table("t", "b"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> table("t", "a", "a")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TargetSchema(DatabaseProduct.MYSQL, "s", List.of(table("t", "a"), table("T", "a"))).tables())
                .as("大文字・小文字の違う名前は別の名前")
                .hasSize(2);
    }

    @Test
    @DisplayName("primary key and foreign key columns must be columns of the table")
    void keyColumnsMustExist() {
        List<TargetColumn> columns = List.of(column("id"), column("ref"));
        assertThatThrownBy(() -> new TargetTable("t", false, null, columns, List.of("missing"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetTable("t", false, null, columns, List.of("id", "id"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        TargetForeignKey unknown = new TargetForeignKey(null, List.of("missing"), "p", List.of("id"));
        assertThatThrownBy(() -> new TargetTable("t", false, null, columns, List.of(), List.of(unknown)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetTable("v", true, null, columns, List.of("id"), List.of()))
                .as("ビューは主キーを持たない")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a foreign key needs at least one column and the same number of referenced columns")
    void foreignKeyCounts() {
        assertThatThrownBy(() -> new TargetForeignKey("fk", List.of(), "p", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetForeignKey("fk", List.of("a", "b"), "p", List.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetForeignKey("fk", List.of("a"), "", List.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TargetForeignKey(null, List.of("a"), "p", List.of("x")).name())
                .isNull();
    }

    @Test
    @DisplayName("required values are checked: names, types, columns and non-negative sizes")
    void requiredValues() {
        assertThatThrownBy(() -> new TargetSchema(null, "s", List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetSchema(DatabaseProduct.MYSQL, "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetTable("t", false, null, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetColumn("", INT, true, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetColumn("a", null, true, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetDbType(" ", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetDbType("x", -1L, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetDbType("x", null, -1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetDbType("x", null, null, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TargetDbType("longtext", 4_294_967_295L, null, null).length())
                .as("32 ビットに収まらない長さもそのまま持つ")
                .isEqualTo(4_294_967_295L);
    }

    @Test
    @DisplayName("a schema without tables is a valid empty copy")
    void emptySchema() {
        assertThat(new TargetSchema(DatabaseProduct.MARIADB, "empty", List.of()).tables())
                .isEmpty();
    }
}
