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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 権限の YAML の書き出し（BR9.1・BR9.14、{@code security-design.md} 5節）の単体テスト。 */
class RoleTransferWriterTest {

    private static final PermissionValues READ = new PermissionValues(MainPermission.READ, null, null);

    private static RoleTransferDocument readBack(String yaml) {
        return ((RoleTransferReader.Valid) RoleTransferReader.read(tree(yaml))).document();
    }

    @Test
    @DisplayName("the roles keep the given order and the targets are written in code point order with their values")
    void layout() {
        Map<PermissionTarget, PermissionValues> sales = new LinkedHashMap<>();
        sales.put(
                PermissionTarget.column("SALES", "ORDERS", "PRICE"),
                new PermissionValues(MainPermission.NONE, null, null));
        sales.put(PermissionTarget.table("SALES", "ORDERS"), new PermissionValues(MainPermission.FULL, true, null));
        sales.put(PermissionTarget.schema("SALES"), new PermissionValues(MainPermission.READ, false, true));
        sales.put(PermissionTarget.schema("HR"), new PermissionValues(null, null, false));

        String yaml = RoleTransferWriter.write(
                List.of(new TransferRoleSettings("営業", sales), new TransferRoleSettings("経理", Map.of())));

        assertThat(yaml).isEqualTo("""
                        version: 1
                        roles:
                          営業:
                            schemas:
                              HR:
                                delete: false
                              SALES:
                                main: READ
                                create: false
                                delete: true
                                tables:
                                  ORDERS:
                                    main: FULL
                                    create: true
                                    columns:
                                      PRICE: NONE
                          経理: {}
                        """);
        assertThat(RoleTransferWriter.write(List.of())).isEqualTo("version: 1\nroles: {}\n");
    }

    @Test
    @DisplayName("names are ordered by code point, so a supplementary character comes after a high BMP character")
    void codePointOrder() {
        String bmpHigh = "\uFF21";
        String supplementary = "\uD840\uDC0B";
        Map<PermissionTarget, PermissionValues> settings = Map.of(
                PermissionTarget.schema(supplementary), READ,
                PermissionTarget.schema(bmpHigh), READ,
                PermissionTarget.schema("a"), READ);

        String yaml = RoleTransferWriter.write(List.of(new TransferRoleSettings("r", settings)));

        assertThat(yaml.indexOf("a:")).isLessThan(yaml.indexOf(bmpHigh));
        assertThat(yaml.indexOf(bmpHigh)).isLessThan(yaml.indexOf(supplementary));
        assertThat(supplementary.compareTo(bmpHigh)).as("UTF-16 の単位の順とは逆になる").isNegative();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "true",
                "False",
                "yes",
                "NO",
                "on",
                "Off",
                "y",
                "null",
                "~",
                "123",
                "1e3",
                "0x1F",
                "2026-10-06",
                ".inf",
                "-a",
                "?a",
                "[a]",
                "{a}",
                ",a",
                "<<",
                "=a",
                "a:b",
                "a #b",
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
                " a",
                "a ",
                "a\\b",
                "\u3000a"
            })
    @DisplayName("names that YAML could read as another type or syntax are double quoted and read back unchanged")
    void quotedNames(String name) {
        String yaml = RoleTransferWriter.write(
                List.of(new TransferRoleSettings("r", Map.of(PermissionTarget.schema(name), READ))));

        assertThat(RoleTransferWriter.needsQuotes(name)).isTrue();
        assertThat(yaml).contains("\n      \"");
        assertThat(readBack(yaml).roles().getFirst().settings()).containsOnlyKeys(PermissionTarget.schema(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"営業", "SALES_DATA", "order line", "a-b", "a.b", "テーブル（月）", "x/y"})
    @DisplayName("plain names are written without quotes")
    void plainNames(String name) {
        assertThat(RoleTransferWriter.needsQuotes(name)).isFalse();
        assertThat(RoleTransferWriter.scalar(name)).isEqualTo(name);
    }

    @Test
    @DisplayName("quotes and backslashes are escaped, and no anchor, alias or tag is written")
    void escapesWithoutAliasesOrTags() {
        String name = "a\"b\\c";
        String yaml = RoleTransferWriter.write(List.of(new TransferRoleSettings(
                "r", Map.of(PermissionTarget.table("S", name), READ, PermissionTarget.schema("S"), READ))));

        assertThat(yaml).contains("\"a\\\"b\\\\c\":");
        assertThat(yaml.replace("\"a\\\"b\\\\c\"", "")).doesNotContain("&", "*", "!");
        assertThat(readBack(yaml).roles().getFirst().settings())
                .containsOnlyKeys(PermissionTarget.schema("S"), PermissionTarget.table("S", name));
    }

    @Test
    @DisplayName("a table with only columns and a column without a main value are written as expected")
    void onlyColumns() {
        String yaml = RoleTransferWriter.write(List.of(new TransferRoleSettings(
                "r",
                Map.of(
                        PermissionTarget.column("S", "T", "C"),
                        READ,
                        PermissionTarget.column("S", "T", "D"),
                        new PermissionValues(null, true, null)))));

        assertThat(yaml)
                .contains("      S:\n        tables:\n          T:\n            columns:\n              C: READ\n");
        assertThat(yaml).doesNotContain("D:");
        assertThat(new TransferRoleSettings("営業", Map.of()).toString()).doesNotContain("営業");
    }
}
