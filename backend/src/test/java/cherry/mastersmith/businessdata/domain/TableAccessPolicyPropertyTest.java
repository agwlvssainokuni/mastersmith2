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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DbType;
import cherry.mastersmith.dsl.domain.DetailSetting;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ListSetting;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SearchSetting;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * 出し分けと許す操作の関数の性質ベースのテスト（jqwik。BR2.5、AC3.1.7・AC3.2.7、team.md の「性質ベースのテスト」）。失敗のときの乱数の
 * 種は、既存のテストの出力の設定（{@code exceptionFormat = FULL}）で残る。
 */
class TableAccessPolicyPropertyTest {

    /** カラム1つの生成の材料。 */
    record ColumnSpec(
            ValueType type,
            boolean equalsSupported,
            boolean password,
            boolean listVisible,
            boolean detailVisible,
            SearchOperator operator,
            MainPermission main) {}

    /** 表1つの生成の材料。 */
    record Spec(
            List<ColumnSpec> columns,
            Set<Integer> keyIndexes,
            boolean view,
            MainPermission tableMain,
            boolean canCreate,
            boolean canDelete,
            boolean writeConfigured) {}

    @Provide
    Arbitrary<Spec> specs() {
        Arbitrary<ColumnSpec> column = Combinators.combine(
                        Arbitraries.of(ValueType.class),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.of(SearchOperator.class).injectNull(0.3),
                        Arbitraries.of(MainPermission.class))
                .as(ColumnSpec::new);
        return Combinators.combine(
                        column.list().ofMinSize(1).ofMaxSize(6),
                        Arbitraries.integers().between(0, 5).set().ofMaxSize(3),
                        Arbitraries.of(true, false),
                        Arbitraries.of(MainPermission.class),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false),
                        Arbitraries.of(true, false))
                .as(Spec::new);
    }

    private static TableAccessView evaluate(Spec spec) {
        Map<String, DslColumn> columns = new LinkedHashMap<>();
        Map<String, MainPermission> columnMain = new HashMap<>();
        Map<String, ColumnKind> kinds = new HashMap<>();
        for (int i = 0; i < spec.columns().size(); i++) {
            ColumnSpec c = spec.columns().get(i);
            String name = "c" + i;
            columns.put(
                    name,
                    new DslColumn(
                            name,
                            new DisplayName(name, name),
                            new DbType("x", null, null, null, true),
                            c.password() ? FormPart.PASSWORD : FormPart.TEXT,
                            new SearchSetting(c.operator() != null, c.operator(), null, false),
                            new ListSetting(c.listVisible(), c.listVisible() ? i + 1 : null, null, true, null, null),
                            new DetailSetting(c.detailVisible()),
                            List.of(),
                            null));
            columnMain.put(name, c.main());
            kinds.put(name, new ColumnKind(c.type(), c.equalsSupported()));
        }
        List<String> key = spec.keyIndexes().stream()
                .filter(i -> i < spec.columns().size())
                .sorted()
                .map(i -> "c" + i)
                .toList();
        DslTable table = new DslTable("t", new DisplayName("t", "t"), spec.view(), key, List.of(), columns);
        return TableAccessPolicy.evaluate(new TableAccessInput(
                "s",
                table,
                BusinessDataDslFixture.DEFINITION,
                spec.tableMain(),
                spec.canCreate(),
                spec.canDelete(),
                columnMain,
                kinds,
                spec.writeConfigured(),
                new KnownIdentifiers(Set.of("s"), Set.of("t"), columns.keySet())));
    }

    private static MainPermission raise(MainPermission main) {
        return switch (main) {
            case NONE -> MainPermission.READ;
            case READ, FULL -> MainPermission.FULL;
        };
    }

    /** すべての権限を1段上げ、補助権限を可にした材料。 */
    private static Spec raised(Spec spec) {
        List<ColumnSpec> columns = new ArrayList<>();
        for (ColumnSpec c : spec.columns()) {
            columns.add(new ColumnSpec(
                    c.type(),
                    c.equalsSupported(),
                    c.password(),
                    c.listVisible(),
                    c.detailVisible(),
                    c.operator(),
                    raise(c.main())));
        }
        return new Spec(
                columns, spec.keyIndexes(), spec.view(), raise(spec.tableMain()), true, true, spec.writeConfigured());
    }

    @Property
    @Label("raising permissions never removes a visible column or an allowed operation")
    void monotone(@ForAll("specs") Spec spec) {
        TableAccessView before = evaluate(spec);
        TableAccessView after = evaluate(raised(spec));

        Set<String> beforeColumns =
                before.columns().stream().map(ColumnAccess::column).collect(Collectors.toSet());
        Set<String> afterColumns =
                after.columns().stream().map(ColumnAccess::column).collect(Collectors.toSet());
        assertThat(afterColumns).containsAll(beforeColumns);
        assertThat(after.operations()).containsAll(before.operations());
    }

    @Property
    @Label("a NONE column is never shown and a column that is not FULL is never an input")
    void noneAndInputs(@ForAll("specs") Spec spec) {
        TableAccessView view = evaluate(spec);

        for (ColumnAccess column : view.columns()) {
            int index = Integer.parseInt(column.column().substring(1));
            ColumnSpec source = spec.columns().get(index);
            assertThat(source.main()).isNotEqualTo(MainPermission.NONE);
            assertThat(spec.tableMain()).isNotEqualTo(MainPermission.NONE);
            if (column.editableOnCreate() || column.editableOnUpdate()) {
                assertThat(column.main()).isEqualTo(MainPermission.FULL);
            }
        }
    }

    @Property
    @Label("no detail, update or delete without a usable primary key, and no create, update or delete for a view")
    void keysAndViews(@ForAll("specs") Spec spec) {
        TableAccessView view = evaluate(spec);
        boolean keyHidden = view.primaryKey().isEmpty()
                || view.primaryKey().stream().anyMatch(name -> view.column(name).isEmpty());

        if (keyHidden) {
            assertThat(view.operations())
                    .doesNotContain(AllowedOperation.DETAIL, AllowedOperation.UPDATE, AllowedOperation.DELETE);
        }
        if (view.view()) {
            assertThat(view.operations())
                    .doesNotContain(AllowedOperation.CREATE, AllowedOperation.UPDATE, AllowedOperation.DELETE);
        }
        if (!spec.writeConfigured()) {
            assertThat(view.operations())
                    .doesNotContain(AllowedOperation.CREATE, AllowedOperation.UPDATE, AllowedOperation.DELETE);
        }
    }
}
