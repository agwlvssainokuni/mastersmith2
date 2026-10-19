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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.SqlIdentifiers;
import java.util.Set;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.StringLength;

/**
 * 識別子の照合と引用の性質ベースのテスト（jqwik。BR3.2・BR3.3、NFR4.1、team.md の「性質ベースのテスト」）。業務データの SQL の部品は、
 * 種類ごとの名前の一覧（{@link KnownIdentifiers}）を {@link SqlIdentifiers#quoteKnown} に渡す。
 */
class IdentifierMatchingPropertyTest {

    @Property
    @Label("a name that is not in the list is refused")
    void unknownNamesAreRefused(
            @ForAll @StringLength(min = 1, max = 30) @Chars({'a', 'B', '\'', '"', '`', ';', '-', '\n', ' ', 'あ'})
                    String name,
            @ForAll DatabaseProduct product) {
        Assume.that(!name.equals("item"));
        KnownIdentifiers names = new KnownIdentifiers(Set.of("s"), Set.of("item"), Set.of("item", "id"));

        assertThatThrownBy(() -> SqlIdentifiers.quoteKnown(product, name, names.tables()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    @Label("any listed name, with quotes, semicolons or newlines, is quoted as exactly one identifier")
    void quotedAsOneIdentifier(
            @ForAll @StringLength(min = 1, max = 30) @Chars({'a', 'B', '\'', '"', '`', ';', '-', '\n', ' ', 'あ'})
                    String name,
            @ForAll DatabaseProduct product) {
        String quoted = SqlIdentifiers.quoteKnown(product, name, Set.of(name));
        char quote = product.identifierQuote();

        assertThat(quoted.charAt(0)).isEqualTo(quote);
        assertThat(quoted.charAt(quoted.length() - 1)).isEqualTo(quote);
        String inner = quoted.substring(1, quoted.length() - 1);
        StringBuilder read = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == quote) {
                assertThat(i + 1).as("中の引用符は二重").isLessThan(inner.length());
                assertThat(inner.charAt(++i)).isEqualTo(quote);
            }
            read.append(c);
        }
        assertThat(read.toString()).as("1つの識別子として読むと元の名前").isEqualTo(name);
    }
}
