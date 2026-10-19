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
package cherry.mastersmith.businessdata.domain;

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.input;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.kind;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 出し分けと許す操作の純粋な関数（BR2.1〜BR2.7、BR5.2〜BR5.4、BR7.9）の単体テスト。 */
class TableAccessPolicyTest {

    private static final DslTable ITEM = table(
            "item",
            false,
            List.of("code"),
            column("code").list(true, 2).search(SearchOperator.EQUALS).build(),
            column("name").list(true, 1).search(SearchOperator.CONTAINS).build(),
            column("secret").form(FormPart.PASSWORD).list(true, 3).build(),
            column("memo").list(false, null).build(),
            column("price")
                    .dbType("numeric")
                    .list(true, 4)
                    .defaultSort(SortDirection.DESC)
                    .build());

    private static final Map<String, ColumnKind> ITEM_KINDS = Map.of("price", kind(ValueType.DECIMAL));

    private static TableAccessView evaluate(
            DslTable table,
            MainPermission tableMain,
            Map<String, MainPermission> columnMain,
            Map<String, ColumnKind> kinds,
            boolean write) {
        return TableAccessPolicy.evaluate(input(table, tableMain, columnMain, kinds, write, write, write));
    }

    @Test
    @DisplayName("columns whose main permission is NONE are never part of the view")
    void noneColumnsAreLeftOut() {
        TableAccessView view = evaluate(
                ITEM,
                MainPermission.READ,
                Map.of("code", MainPermission.READ, "name", MainPermission.READ, "memo", MainPermission.FULL),
                ITEM_KINDS,
                false);

        assertThat(view.columns()).extracting(ColumnAccess::column).containsExactly("code", "name", "memo");
        assertThat(view.column("secret")).isEmpty();
        assertThat(view.column("price")).isEmpty();
        assertThat(view.listColumns()).extracting(ColumnAccess::column).containsExactly("name", "code");
        assertThat(view.detailColumns()).extracting(ColumnAccess::column).containsExactly("code", "name", "memo");
    }

    @Test
    @DisplayName("a table whose main permission is NONE has no column and no operation")
    void noneTable() {
        TableAccessView view = evaluate(
                ITEM,
                MainPermission.NONE,
                Map.of("code", MainPermission.FULL, "name", MainPermission.FULL),
                ITEM_KINDS,
                true);

        assertThat(view.columns()).isEmpty();
        assertThat(view.operations()).isEmpty();
        assertThat(view.rowLabelColumns()).isEmpty();
    }

    @Test
    @DisplayName("operations follow the FR9.1 table for tables and views")
    void operationsFollowTheTable() {
        Map<String, MainPermission> full =
                Map.of("code", MainPermission.FULL, "name", MainPermission.FULL, "price", MainPermission.FULL);

        assertThat(evaluate(ITEM, MainPermission.READ, full, ITEM_KINDS, false).operations())
                .as("書き込みの設定が無ければ一覧と詳細だけ")
                .containsExactlyInAnyOrder(AllowedOperation.LIST, AllowedOperation.DETAIL);
        assertThat(evaluate(ITEM, MainPermission.FULL, full, ITEM_KINDS, true).operations())
                .containsExactlyInAnyOrder(
                        AllowedOperation.LIST,
                        AllowedOperation.DETAIL,
                        AllowedOperation.CREATE,
                        AllowedOperation.UPDATE,
                        AllowedOperation.DELETE);
        DslTable view = table(
                ITEM.name(), true, ITEM.primaryKey(), ITEM.columns().values().toArray(new DslColumn[0]));
        TableAccessView viewAccess = evaluate(view, MainPermission.FULL, full, ITEM_KINDS, true);
        assertThat(viewAccess.operations()).containsExactlyInAnyOrder(AllowedOperation.LIST, AllowedOperation.DETAIL);
        assertThat(viewAccess.restrictionReason()).isEqualTo(RestrictionReason.VIEW);
        assertThat(evaluate(ITEM, MainPermission.FULL, full, ITEM_KINDS, true).restrictionReason())
                .isNull();
    }

    @Test
    @DisplayName("a table without a primary key allows only the list and says NO_PRIMARY_KEY")
    void noPrimaryKey() {
        DslTable log = table(
                "log", false, List.of(), column("at").build(), column("text").build());

        TableAccessView view = evaluate(
                log,
                MainPermission.FULL,
                Map.of("at", MainPermission.FULL, "text", MainPermission.FULL),
                Map.of(),
                true);

        assertThat(view.operations()).containsExactlyInAnyOrder(AllowedOperation.LIST, AllowedOperation.CREATE);
        assertThat(view.restrictionReason()).isEqualTo(RestrictionReason.NO_PRIMARY_KEY);
        assertThat(view.keyColumns()).isEmpty();
        assertThat(view.orderKeys(Optional.empty())).as("順を約束しない").isEmpty();
    }

    @Test
    @DisplayName("a primary key column that is NONE removes the detail and says PRIMARY_KEY_HIDDEN")
    void hiddenPrimaryKey() {
        DslTable line = table(
                "line",
                false,
                List.of("order_no", "line_no"),
                column("order_no").build(),
                column("line_no").build(),
                column("qty").build());

        TableAccessView view = evaluate(
                line,
                MainPermission.READ,
                Map.of("order_no", MainPermission.READ, "qty", MainPermission.READ),
                Map.of(),
                false);

        assertThat(view.operations()).containsExactly(AllowedOperation.LIST);
        assertThat(view.restrictionReason()).isEqualTo(RestrictionReason.PRIMARY_KEY_HIDDEN);
        assertThat(view.orderKeys(Optional.empty()))
                .as("見られない主キーでも並びの安定には使う（値は返さない）")
                .containsExactly(new SortKey("order_no", SortDirection.ASC), new SortKey("line_no", SortDirection.ASC));
    }

    @Test
    @DisplayName("a primary key of a binary type or of an OTHER type without equality removes the detail")
    void unusableKeyTypes() {
        DslTable blobKey = table(
                "blob_key",
                false,
                List.of("k"),
                column("k").dbType("bytea").build(),
                column("v").build());
        DslTable jsonKey = table(
                "json_key",
                false,
                List.of("k"),
                column("k").dbType("json").build(),
                column("v").build());
        DslTable uuidKey = table(
                "uuid_key",
                false,
                List.of("k"),
                column("k").dbType("uuid").build(),
                column("v").build());
        Map<String, MainPermission> read = Map.of("k", MainPermission.READ, "v", MainPermission.READ);

        assertThat(evaluate(blobKey, MainPermission.READ, read, Map.of("k", kind(ValueType.BINARY)), false)
                        .operations())
                .containsExactly(AllowedOperation.LIST);
        TableAccessView json = evaluate(
                jsonKey, MainPermission.READ, read, Map.of("k", new ColumnKind(ValueType.OTHER, false)), false);
        assertThat(json.operations()).containsExactly(AllowedOperation.LIST);
        assertThat(json.restrictionReason()).isNull();
        assertThat(evaluate(uuidKey, MainPermission.READ, read, Map.of("k", kind(ValueType.OTHER)), false)
                        .operations())
                .containsExactlyInAnyOrder(AllowedOperation.LIST, AllowedOperation.DETAIL);
    }

    @Test
    @DisplayName("the row label is the primary key when it is listed, otherwise the first listed column")
    void rowLabelColumns() {
        Map<String, MainPermission> read =
                Map.of("code", MainPermission.READ, "name", MainPermission.READ, "secret", MainPermission.READ);
        assertThat(evaluate(ITEM, MainPermission.READ, read, ITEM_KINDS, false).rowLabelColumns())
                .containsExactly("code");

        DslTable hiddenKey = table(
                "t",
                false,
                List.of("id"),
                column("id").list(false, null).build(),
                column("secret").form(FormPart.PASSWORD).list(true, 1).build(),
                column("title").list(true, 2).build());
        Map<String, MainPermission> all =
                Map.of("id", MainPermission.READ, "secret", MainPermission.READ, "title", MainPermission.READ);
        assertThat(evaluate(hiddenKey, MainPermission.READ, all, Map.of(), false)
                        .rowLabelColumns())
                .as("password は飛ばす")
                .containsExactly("title");
    }

    @Test
    @DisplayName("input, sort and default sort exclude password, binary and OTHER columns")
    void editableAndSortableFlags() {
        DslTable mixed = table(
                "mixed",
                false,
                List.of("id"),
                column("id").defaultSort(SortDirection.ASC).build(),
                column("pw")
                        .form(FormPart.PASSWORD)
                        .defaultSort(SortDirection.ASC)
                        .build(),
                column("bin").dbType("bytea").build(),
                column("other").dbType("uuid").defaultSort(SortDirection.DESC).build(),
                column("note").detail(false).build());
        Map<String, MainPermission> full = Map.of(
                "id",
                MainPermission.FULL,
                "pw",
                MainPermission.FULL,
                "bin",
                MainPermission.FULL,
                "other",
                MainPermission.FULL,
                "note",
                MainPermission.FULL);
        TableAccessView view = evaluate(
                mixed,
                MainPermission.FULL,
                full,
                Map.of("bin", kind(ValueType.BINARY), "other", kind(ValueType.OTHER)),
                true);

        ColumnAccess id = view.column("id").orElseThrow();
        assertThat(id.editableOnCreate()).isTrue();
        assertThat(id.editableOnUpdate()).as("主キーは更新しない").isFalse();
        assertThat(view.column("pw").orElseThrow().editableOnUpdate()).isTrue();
        assertThat(view.column("pw").orElseThrow().sortable()).isFalse();
        assertThat(view.column("pw").orElseThrow().masked()).isTrue();
        assertThat(view.column("bin").orElseThrow().editableOnCreate()).isFalse();
        assertThat(view.column("bin").orElseThrow().sortable()).isFalse();
        assertThat(view.column("other").orElseThrow().editableOnCreate()).isFalse();
        assertThat(view.column("other").orElseThrow().sortable()).isFalse();
        assertThat(view.column("note").orElseThrow().editableOnCreate())
                .as("詳細に出さないカラム")
                .isFalse();
        assertThat(view.defaultSortKeys()).containsExactly(new SortKey("id", SortDirection.ASC));
        assertThat(view.orderKeys(Optional.of(new SortKey("note", SortDirection.DESC))))
                .containsExactly(new SortKey("note", SortDirection.DESC), new SortKey("id", SortDirection.ASC));
        assertThat(view.columns())
                .allSatisfy(c -> assertThat(c.validation()).as("U1 は検証の材料を埋めない").isNull());
    }

    @Test
    @DisplayName("the view lists the known identifiers by kind and hides names in its string form")
    void identifiersAndStringForm() {
        TableAccessView view = BusinessDataDslFixture.view(ITEM, MainPermission.READ, ITEM_KINDS);

        assertThat(view.knownIdentifiers().schemas()).containsExactly("sample");
        assertThat(view.knownIdentifiers().tables()).containsExactly("item");
        assertThat(view.knownIdentifiers().columns())
                .containsExactlyInAnyOrder("code", "name", "secret", "memo", "price");
        assertThat(view.toString()).doesNotContain("項目").doesNotContain("Item ");
        assertThat(view.knownIdentifiers().toString()).doesNotContain("code");
        assertThat(view.searchColumns()).extracting(ColumnAccess::column).containsExactly("code", "name");
    }
}
