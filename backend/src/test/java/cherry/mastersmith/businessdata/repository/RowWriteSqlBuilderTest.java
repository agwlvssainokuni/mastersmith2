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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnKind;
import cherry.mastersmith.businessdata.domain.KnownIdentifiers;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.TableAccessInput;
import cherry.mastersmith.businessdata.domain.TableAccessPolicy;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TypedValue;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 書き込みの SQL の文字の組み立ての単体テスト（Intent 261016-business-data-views の U2、BR5.1・BR6.2・BR7.1）。 */
class RowWriteSqlBuilderTest {

    /** 記号を含む名前（引用符を二重にして引用する）。 */
    private static final String SYMBOL = "c\"x'; --`\n";

    private static final DslTable ITEM = BusinessDataDslFixture.tableWithForeignKeys(
            "item",
            List.of("id"),
            List.of(
                    new DslForeignKey(List.of("maker"), "maker", List.of("id")),
                    new DslForeignKey(List.of("a", "b"), "pair", List.of("x", "y"))),
            column("id").dbType("int4").build(),
            column("code")
                    .validation(ValidationType.UNIQUE, ValidationOrigin.MANUAL)
                    .build(),
            column("mail")
                    .validation(ValidationType.UNIQUE, ValidationOrigin.MANUAL)
                    .build(),
            column("maker").dbType("int4").build(),
            column("a").build(),
            column("b").build(),
            column(SYMBOL).build());

    private static TableAccessView view(DslTable table) {
        Map<String, MainPermission> main = new HashMap<>();
        Map<String, ColumnKind> kinds = new HashMap<>();
        table.columns().keySet().forEach(name -> {
            main.put(name, MainPermission.FULL);
            kinds.put(name, new ColumnKind(ValueType.STRING, true));
        });
        kinds.put("id", new ColumnKind(ValueType.INTEGER, true));
        return TableAccessPolicy.evaluate(new TableAccessInput(
                "sample",
                table,
                BusinessDataDslFixture.DEFINITION,
                MainPermission.FULL,
                true,
                true,
                main,
                kinds,
                true,
                new KnownIdentifiers(
                        Set.of("sample"),
                        Set.of("item", "maker", "pair"),
                        table.columns().keySet()),
                Map.of("maker", Set.of("id", "name"), "pair", Set.of("x", "y"))));
    }

    private static TypedValue text(String column, String value) {
        return new TypedValue(column, new BoundValue(ValueType.STRING, value));
    }

    private static final RowKey KEY = new RowKey(Map.of("id", new BoundValue(ValueType.INTEGER, 7L)));

    private static RowWriteSqlBuilder postgres() {
        return new RowWriteSqlBuilder(RowDialect.of(DatabaseProduct.POSTGRESQL), view(ITEM));
    }

    @Test
    @DisplayName("the create statement lists only the sent columns, quoted and qualified, with bound values")
    void insert() {
        RowWriteSqlBuilder.Statement insert = postgres().insert(List.of(text("code", "A'1"), text(SYMBOL, "x")));

        assertThat(insert.sql())
                .isEqualTo("INSERT INTO \"sample\".\"item\" (\"code\", \"c\"\"x'; --`\n\") VALUES (?, ?)");
        assertThat(insert.values()).extracting(BoundValue::value).containsExactly("A'1", "x");
        assertThat(new RowWriteSqlBuilder(RowDialect.of(DatabaseProduct.MYSQL), view(ITEM))
                        .insert(List.of())
                        .sql())
                .isEqualTo("INSERT INTO `sample`.`item` () VALUES ()");
    }

    @Test
    @DisplayName("the update sets only the sent columns and points at the row by the key, binding the key last")
    void update() {
        RowWriteSqlBuilder.Statement update = new RowWriteSqlBuilder(RowDialect.of(DatabaseProduct.MARIADB), view(ITEM))
                .update(List.of(text("code", "x"), text("a", null)), KEY);

        assertThat(update.sql()).isEqualTo("UPDATE `sample`.`item` SET `code` = ?, `a` = ? WHERE `id` = ?");
        assertThat(update.values()).extracting(BoundValue::value).containsExactly("x", null, 7L);
        assertThatThrownBy(() -> postgres().update(List.of(), KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the delete points at the row by the key")
    void delete() {
        RowWriteSqlBuilder.Statement delete = postgres().delete(KEY);

        assertThat(delete.sql()).isEqualTo("DELETE FROM \"sample\".\"item\" WHERE \"id\" = ?");
        assertThat(delete.values()).extracting(BoundValue::value).containsExactly(7L);
    }

    @Test
    @DisplayName("the unique probe asks all sent unique columns in one statement and skips NULL values")
    void uniqueProbeOnCreate() {
        RowWriteSqlBuilder.Statement probe = postgres()
                .uniqueProbe(List.of(text("code", "A"), text("mail", null), text("a", "z")), null)
                .orElseThrow();

        assertThat(probe.sql())
                .isEqualTo("SELECT (SELECT 1 FROM \"sample\".\"item\" WHERE \"code\" = ? LIMIT 1) AS c0");
        assertThat(probe.probes()).containsExactly("code");
        assertThat(postgres().uniqueProbe(List.of(text("a", "z")), null)).isEmpty();
    }

    @Test
    @DisplayName("the unique probe of an update excludes the own row, for single and composite keys")
    void uniqueProbeExcludesTheOwnRow() {
        RowWriteSqlBuilder.Statement single = postgres()
                .uniqueProbe(List.of(text("code", "A"), text("mail", "m")), KEY)
                .orElseThrow();
        assertThat(single.sql())
                .isEqualTo("SELECT (SELECT 1 FROM \"sample\".\"item\" WHERE \"code\" = ? AND NOT (\"id\" = ?) LIMIT 1)"
                        + " AS c0, (SELECT 1 FROM \"sample\".\"item\" WHERE \"mail\" = ? AND NOT (\"id\" = ?) LIMIT 1)"
                        + " AS c1");
        assertThat(single.values()).extracting(BoundValue::value).containsExactly("A", 7L, "m", 7L);

        DslTable composite = BusinessDataDslFixture.tableWithForeignKeys(
                "item",
                List.of("a", "b"),
                List.of(),
                column("a").build(),
                column("b").build(),
                column("code")
                        .validation(ValidationType.UNIQUE, ValidationOrigin.MANUAL)
                        .build());
        Map<String, BoundValue> key = new LinkedHashMap<>();
        key.put("a", new BoundValue(ValueType.STRING, "1"));
        key.put("b", new BoundValue(ValueType.STRING, "2"));
        RowWriteSqlBuilder.Statement both = new RowWriteSqlBuilder(
                        RowDialect.of(DatabaseProduct.MYSQL), view(composite))
                .uniqueProbe(List.of(text("code", "A")), new RowKey(key))
                .orElseThrow();
        assertThat(both.sql())
                .isEqualTo("SELECT (SELECT 1 FROM `sample`.`item` WHERE `code` = ? AND NOT (`a` = ? AND `b` = ?)"
                        + " LIMIT 1) AS c0");
    }

    @Test
    @DisplayName("the reference probe asks each fully sent foreign key against its referenced table")
    void referenceProbe() {
        RowWriteSqlBuilder.Statement probe = postgres()
                .referenceProbe(List.of(text("maker", "3"), text("a", "p"), text("b", "q")))
                .orElseThrow();

        assertThat(probe.sql())
                .isEqualTo("SELECT (SELECT 1 FROM \"sample\".\"maker\" WHERE \"id\" = ? LIMIT 1) AS c0,"
                        + " (SELECT 1 FROM \"sample\".\"pair\" WHERE \"x\" = ? AND \"y\" = ? LIMIT 1) AS c1");
        assertThat(probe.probes()).containsExactly("maker", "a");
        assertThat(postgres().referenceProbe(List.of(text("a", "p"))))
                .as("一部だけ送った外部キーは確かめない")
                .isEmpty();
        assertThat(postgres().referenceProbe(List.of(text("maker", null)))).isEmpty();
    }

    @Test
    @DisplayName("names outside the known identifiers make no statement, whatever symbols they contain")
    void unknownNames() {
        for (String name : List.of("unknown", "code\"; DROP TABLE item; --", "`code`", "code\n")) {
            assertThatThrownBy(() -> postgres().insert(List.of(text(name, "x"))))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(postgres().missingKeyColumns(List.of(text("code", "x")))).containsExactly("id");
        assertThat(postgres().missingKeyColumns(List.of(new TypedValue("id", new BoundValue(ValueType.INTEGER, 1L)))))
                .isEmpty();
    }
}
