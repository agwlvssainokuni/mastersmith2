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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.PermissionInheritance.Resolution;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * 継承の解決の性質ベースのテスト（jqwik。BR5.1・BR5.2・BR5.6、AC1.2.9、NFR6.2）。
 *
 * <p>名前は {@code Properties} で終わらせない（{@code *Test}・{@code *IT} のどちらにも当たらず動かないため。計画の D-12）。失敗のときの
 * 乱数の種は、既存のテストの出力の設定（{@code exceptionFormat = FULL}）で残る。
 */
class PermissionInheritancePropertyTest {

    @Provide
    Arbitrary<PermissionValues> values() {
        Arbitrary<MainPermission> main = Arbitraries.of(MainPermission.class).injectNull(0.4);
        Arbitrary<Boolean> aux = Arbitraries.of(true, false).injectNull(0.4);
        return Combinators.combine(main, aux, aux).as(PermissionValues::new);
    }

    @Provide
    Arbitrary<PermissionValues> columnValues() {
        return Arbitraries.of(MainPermission.class).injectNull(0.4).map(main -> new PermissionValues(main, null, null));
    }

    @Property
    @Label("the column result equals column ?? table ?? schema ?? NONE and table ?? schema ?? false")
    void columnFollowsTheFormula(
            @ForAll("values") PermissionValues schema,
            @ForAll("values") PermissionValues table,
            @ForAll("columnValues") PermissionValues column) {
        Resolution resolution = PermissionInheritance.resolve(PermissionLevel.COLUMN, schema, table, column);

        MainPermission main = column.main() != null
                ? column.main()
                : table.main() != null ? table.main() : schema.main() != null ? schema.main() : MainPermission.NONE;
        boolean create = table.create() != null ? table.create() : schema.create() != null && schema.create();
        boolean delete = table.delete() != null ? table.delete() : schema.delete() != null && schema.delete();
        assertThat(resolution.effective()).isEqualTo(new EffectivePermission(main, create, delete));
    }

    @Property
    @Label("an explicit value at a lower level overrides any upper value")
    void lowerExplicitOverrides(
            @ForAll("values") PermissionValues schema,
            @ForAll("values") PermissionValues table,
            @ForAll MainPermission explicit) {
        Resolution resolution = PermissionInheritance.resolve(
                PermissionLevel.COLUMN, schema, table, new PermissionValues(explicit, null, null));

        assertThat(resolution.effective().main()).isEqualTo(explicit);
        assertThat(resolution.mainFrom()).isEqualTo(InheritedFrom.EXPLICIT);
    }

    @Property
    @Label("CREATE and DELETE do not affect each other")
    void auxiliariesAreIndependent(
            @ForAll("values") PermissionValues schema,
            @ForAll("values") PermissionValues table,
            @ForAll Boolean other) {
        PermissionValues changedSchema = new PermissionValues(schema.main(), schema.create(), other);
        PermissionValues changedTable = new PermissionValues(table.main(), table.create(), other);

        boolean before = PermissionInheritance.resolve(PermissionLevel.TABLE, schema, table, null)
                .effective()
                .create();
        boolean after = PermissionInheritance.resolve(PermissionLevel.TABLE, changedSchema, changedTable, null)
                .effective()
                .create();

        assertThat(after).isEqualTo(before);
    }

    @Property
    @Label("the auxiliary permissions of a column are the effective ones of its table")
    void columnAuxiliaryIsTheTable(
            @ForAll("values") PermissionValues schema,
            @ForAll("values") PermissionValues table,
            @ForAll("columnValues") PermissionValues column) {
        EffectivePermission ofColumn = PermissionInheritance.resolve(PermissionLevel.COLUMN, schema, table, column)
                .effective();
        EffectivePermission ofTable = PermissionInheritance.resolve(PermissionLevel.TABLE, schema, table, null)
                .effective();

        assertThat(ofColumn.create()).isEqualTo(ofTable.create());
        assertThat(ofColumn.delete()).isEqualTo(ofTable.delete());
    }
}
