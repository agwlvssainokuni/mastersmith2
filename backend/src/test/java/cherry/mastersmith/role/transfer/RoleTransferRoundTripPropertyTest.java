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
package cherry.mastersmith.role.transfer;

import static cherry.mastersmith.role.testsupport.TransferYamlTrees.tree;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.RoleName;
import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.role.transfer.TransferPlanner.CurrentRole;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * 書き出しと読み込みの往復の性質ベースのテスト（jqwik。AC3.1.14・BR9.9・BR9.14、{@code security-design.md} 5節、試し U1、NFR6.2）。
 *
 * <p>名前には YAML で型や特別な意味を持ちうる語と記号（{@code true}・{@code false}・{@code null}・{@code ~}・{@code 123}・
 * {@code yes}・{@code on}・日付・{@code :}・{@code #}・{@code &}・{@code *}・{@code !}・{@code |}・{@code >}・{@code '}・{@code "}・
 * {@code %}・{@code @}・{@code `}・前後の空白）を混ぜる。名前は {@code Properties} で終わらせない（計画の D-12）。失敗のときの乱数の種は、
 * 既存のテストの出力の設定（{@code exceptionFormat = FULL}）で残る。
 */
class RoleTransferRoundTripPropertyTest {

    private static final List<String> TRICKY = List.of(
            "true",
            "false",
            "null",
            "~",
            "123",
            "yes",
            "on",
            "2026-10-06",
            "a:b",
            "#x",
            "&a",
            "*a",
            "!a",
            "|a",
            ">a",
            "'a",
            "\"a",
            "%a",
            "@a",
            "`a",
            "Yes",
            "NULL",
            "0x1F",
            "1e3",
            "-a",
            "?a",
            "[a]",
            "{a}",
            "a,b",
            "<<",
            "a\\b",
            "営業",
            "テーブル",
            "𠀋",
            "Ａ");

    @Provide
    Arbitrary<String> targetNames() {
        Arbitrary<String> tricky = Arbitraries.of(TRICKY);
        Arbitrary<String> padded = Combinators.combine(Arbitraries.of(TRICKY), Arbitraries.of("", " ", "  ", "　"))
                .as((name, space) -> space + name + space);
        Arbitrary<String> plain = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('_', ' ', '-', '.', '/')
                .ofMinLength(1)
                .ofMaxLength(12);
        return Arbitraries.oneOf(tricky, padded, plain);
    }

    @Provide
    Arbitrary<String> roleNames() {
        Arbitrary<String> plain = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('_', '-')
                .ofMinLength(1)
                .ofMaxLength(10);
        return Arbitraries.oneOf(Arbitraries.of(TRICKY), plain)
                .filter(name -> RoleName.parse(name) instanceof RoleNameValidation.Valid(RoleName parsed)
                        && parsed.value().equals(name));
    }

    @Provide
    Arbitrary<PermissionValues> tableValues() {
        Arbitrary<MainPermission> main = Arbitraries.of(MainPermission.class).injectNull(0.3);
        Arbitrary<Boolean> aux = Arbitraries.of(true, false).injectNull(0.4);
        return Combinators.combine(main, aux, aux).as(PermissionValues::new).filter(values -> !values.isEmpty());
    }

    @Provide
    Arbitrary<List<TransferRoleSettings>> roles() {
        Arbitrary<Map<PermissionTarget, PermissionValues>> settings = Combinators.combine(
                        targetNames().list().ofMinSize(1).ofMaxSize(3),
                        targetNames().list().ofMaxSize(3),
                        tableValues().list().ofMinSize(6).ofMaxSize(6),
                        Arbitraries.of(MainPermission.class))
                .as((schemas, tables, values, columnMain) -> {
                    Map<PermissionTarget, PermissionValues> map = new LinkedHashMap<>();
                    int i = 0;
                    for (String schema : schemas) {
                        map.put(PermissionTarget.schema(schema), values.get(i++ % values.size()));
                        for (String table : tables) {
                            map.put(PermissionTarget.table(schema, table), values.get(i++ % values.size()));
                            map.put(
                                    PermissionTarget.column(schema, table, table + "_c"),
                                    new PermissionValues(columnMain, null, null));
                        }
                    }
                    return map;
                });
        return Combinators.combine(roleNames(), settings)
                .as(TransferRoleSettings::new)
                .list()
                .ofMaxSize(4)
                .uniqueElements(role -> ((RoleNameValidation.Valid) RoleName.parse(role.name()))
                        .name()
                        .key());
    }

    private static RoleTransferDocument readBack(String yaml) {
        RoleTransferReader.Result result = RoleTransferReader.read(tree(yaml));
        assertThat(result).as(yaml).isInstanceOf(RoleTransferReader.Valid.class);
        return ((RoleTransferReader.Valid) result).document();
    }

    @Property
    @Label("writing and reading back gives the same role names and settings")
    void roundTrip(@ForAll("roles") List<TransferRoleSettings> roles) {
        RoleTransferDocument document = readBack(RoleTransferWriter.write(roles));

        assertThat(document.roles()).hasSameSizeAs(roles);
        for (int i = 0; i < roles.size(); i++) {
            assertThat(document.roles().get(i).name().value())
                    .isEqualTo(roles.get(i).name());
            assertThat(document.roles().get(i).settings())
                    .isEqualTo(roles.get(i).settings());
        }
    }

    @Property
    @Label("reading back the export of the current settings changes nothing")
    void noChangeAfterRoundTrip(@ForAll("roles") List<TransferRoleSettings> roles) {
        List<CurrentRole> current = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        long id = 1;
        for (TransferRoleSettings role : roles) {
            String key = ((RoleNameValidation.Valid) RoleName.parse(role.name()))
                    .name()
                    .key();
            keys.add(key);
            current.add(new CurrentRole(id++, role.name(), key, role.settings()));
        }

        TransferPlan plan =
                TransferPlanner.plan(readBack(RoleTransferWriter.write(roles)), current, target -> true, "dsl");

        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.untouchedRoles()).isEmpty();
        assertThat(keys).hasSize(roles.size());
    }
}
