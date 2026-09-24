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
package cherry.mastersmith.dsl.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 節の木から JSON の形と位置の対応表を作る変換の単体テスト（ADR-008、BR4.1・BR4.2）。 */
class YamlTreeConverterTest {

    private static final String YAML = """
            version: 1
            menus:
              - label: {ja: 部署, en: Dept}
                table: dept
            tables:
              dept:
                label: &name
                  ja: 部署
                  en: dept
                columns:
                  code:
                    label: *name
            """;

    @Test
    @DisplayName("each key records the position of the key and each list item records where the item starts")
    void positionsOfKeysAndItems() {
        PositionMap positions = SafeYamlParserTest.parsed(YAML).positions();

        assertThat(positions.find("")).contains(new YamlPosition(1, 1));
        assertThat(positions.find("/version")).contains(new YamlPosition(1, 1));
        assertThat(positions.find("/menus")).contains(new YamlPosition(2, 1));
        assertThat(positions.find("/menus/0")).contains(new YamlPosition(3, 5));
        assertThat(positions.find("/menus/0/table")).contains(new YamlPosition(4, 5));
        assertThat(positions.find("/menus/0/label/en")).contains(new YamlPosition(3, 21));
        assertThat(positions.find("/tables/dept/columns/code")).contains(new YamlPosition(11, 7));
        assertThat(positions.find("/tables/dept/label/ja")).contains(new YamlPosition(8, 7));
        assertThat(positions.find("/no/such")).isEmpty();
    }

    @Test
    @DisplayName("values reached through an alias are located at the key that wrote the alias reference")
    void aliasValuesUseTheReferencePosition() {
        YamlDocument document = SafeYamlParserTest.parsed(YAML);

        assertThat(document.json().at("/tables/dept/columns/code/label/ja").stringValue())
                .isEqualTo("部署");
        assertThat(document.positions().find("/tables/dept/columns/code/label")).contains(new YamlPosition(12, 9));
        assertThat(document.positions().find("/tables/dept/columns/code/label/ja"))
                .contains(new YamlPosition(12, 9));
        assertThat(document.positions().find("/tables/dept/columns/code/label/en"))
                .contains(new YamlPosition(12, 9));
    }

    @Test
    @DisplayName("an alias used as a list item is located where the alias is written")
    void aliasListItemUsesAliasPosition() {
        YamlDocument document = SafeYamlParserTest.parsed("base: &b {x: 1}\nlist:\n  - *b\n  - y: 2\n");

        assertThat(document.positions().find("/list/0")).contains(new YamlPosition(3, 5));
        assertThat(document.positions().find("/list/0/x")).contains(new YamlPosition(3, 5));
        assertThat(document.positions().find("/list/1/y")).contains(new YamlPosition(4, 5));
    }

    @Test
    @DisplayName("scalars become JSON values of the type YAML resolves implicitly")
    void scalarTypes() {
        JsonNode json = SafeYamlParserTest.parsed("""
                        s: text
                        q: "1"
                        i: 12
                        big: 123456789012345678901234567890
                        hex: 0x1F
                        oct: 017
                        bin: 0b101
                        under: 1_000
                        neg: -5
                        f: 1.5
                        inf: .inf
                        yes: yes
                        off: off
                        t: true
                        n: null
                        tilde: ~
                        empty:
                        date: 2026-09-24
                        sexa: 1:30
                        """).json();

        assertThat(json.get("s").stringValue()).isEqualTo("text");
        assertThat(json.get("q").isString()).isTrue();
        assertThat(json.get("i").intValue()).isEqualTo(12);
        assertThat(json.get("big").bigIntegerValue()).isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(json.get("hex").intValue()).isEqualTo(31);
        assertThat(json.get("oct").intValue()).isEqualTo(15);
        assertThat(json.get("bin").intValue()).isEqualTo(5);
        assertThat(json.get("under").intValue()).isEqualTo(1000);
        assertThat(json.get("neg").intValue()).isEqualTo(-5);
        assertThat(json.get("f").decimalValue()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(json.get("inf").isString()).isTrue();
        assertThat(json.get("yes").booleanValue()).isTrue();
        assertThat(json.get("off").booleanValue()).isFalse();
        assertThat(json.get("t").booleanValue()).isTrue();
        assertThat(json.get("n").isNull()).isTrue();
        assertThat(json.get("tilde").isNull()).isTrue();
        assertThat(json.get("empty").isNull()).isTrue();
        assertThat(json.get("date").stringValue()).isEqualTo("2026-09-24");
        assertThat(json.get("sexa").isString()).isTrue();
    }

    @Test
    @DisplayName("number forms that cannot be read stay as text")
    void unreadableNumbersStayText() {
        assertThat(YamlTreeConverter.parseInteger("0xZZ")).isNull();
        assertThat(YamlTreeConverter.parseInteger("+7")).isEqualTo(BigInteger.valueOf(7));
        assertThat(YamlTreeConverter.parseInteger("0")).isEqualTo(BigInteger.ZERO);
        assertThat(YamlTreeConverter.parseDecimal("-.NaN")).isNull();
        assertThat(YamlTreeConverter.parseDecimal("1.2.3")).isNull();
        assertThat(YamlTreeConverter.parseDecimal("1:30.5")).isNull();
    }

    @Test
    @DisplayName("JSON pointers escape slashes and tildes and become dotted paths")
    void jsonPointers() {
        String pointer = JsonPointers.child(JsonPointers.child("", "a/b~c"), 2);

        assertThat(pointer).isEqualTo("/a~1b~0c/2");
        assertThat(JsonPointers.toPath(pointer)).isEqualTo("a/b~c.2");
        assertThat(JsonPointers.toPath("")).isNull();
        assertThat(JsonPointers.toPath(null)).isNull();
    }
}
