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

import java.nio.charset.StandardCharsets;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

/**
 * YAML の読み込みの性質ベースのテスト（NFR5.1）。任意の入力でも、想定外の例外が漏れず、読めた結果か誤り1件が返る。
 * 失敗したときの乱数の種は jqwik の報告に出る（{@code @Property(seed = "...")} に与えて再現する）。
 */
class SafeYamlParserPropertyTest {

    private final SafeYamlParser parser = new SafeYamlParser();

    @Property(tries = 300)
    @Label("arbitrary bytes never leak an unexpected exception")
    void arbitraryBytes(@ForAll @Size(max = 300) byte[] bytes) {
        assertThat(parser.parse(bytes)).isNotNull();
    }

    @Property(tries = 500)
    @Label("arbitrary YAML-like text never leaks an unexpected exception and errors carry no component text")
    void arbitraryYamlLikeText(
            @ForAll
                    @StringLength(max = 200)
                    @Chars({
                        'a', 'b', ':', ' ', '\n', '-', '[', ']', '{', '}', ',', '&', '*', '!', '?', '|', '>', '"', '\'',
                        '#', '%', '@', '\t', '1', '.'
                    })
                    String text) {
        YamlParseResult result = parser.parse(text.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        if (result instanceof YamlParseResult.Rejected rejected) {
            assertThat(rejected.error().messageKey()).startsWith("dsl.");
            assertThat(String.join(" ", rejected.error().messageArgs())).doesNotContain("Exception");
        }
    }
}
