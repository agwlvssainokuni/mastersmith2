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

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.StringLength;

/** 部分一致のエスケープの性質ベースのテスト（jqwik。BR4.3、security-design.md の 8節）。 */
class ContainsPatternPropertyTest {

    @Property
    @Label(
            "after escaping, no percent sign or underscore is left without the escape character and the value comes back")
    void noBareWildcards(
            @ForAll @StringLength(max = 50) @Chars({'%', '_', '!', '\\', 'a', 'あ', '\'', '"', ';', '\n'})
                    String value) {
        String pattern = ContainsPattern.of(value);

        assertThat(pattern).startsWith("%").endsWith("%");
        String inner = pattern.substring(1, pattern.length() - 1);
        StringBuilder unescaped = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == ContainsPattern.ESCAPE) {
                assertThat(i + 1).as("エスケープの文字の後に文字がある").isLessThan(inner.length());
                char next = inner.charAt(++i);
                assertThat(next).isIn('!', '%', '_');
                unescaped.append(next);
            } else {
                assertThat(c).as("前置の無いワイルドカード").isNotIn('%', '_');
                unescaped.append(c);
            }
        }
        assertThat(unescaped.toString()).isEqualTo(value);
    }
}
