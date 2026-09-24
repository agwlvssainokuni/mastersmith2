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
package cherry.mastersmith.targetdb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 識別子の引用符の単体テスト（BR2.4、NFR6.1）。 */
class SqlIdentifiersTest {

    @Test
    @DisplayName("MySQL and MariaDB use backquotes and PostgreSQL uses double quotes, doubling the quote inside")
    void quotesByProduct() {
        assertThat(SqlIdentifiers.quote(DatabaseProduct.MYSQL, "order")).isEqualTo("`order`");
        assertThat(SqlIdentifiers.quote(DatabaseProduct.MARIADB, "a`b")).isEqualTo("`a``b`");
        assertThat(SqlIdentifiers.quote(DatabaseProduct.POSTGRESQL, "Mixed Case"))
                .isEqualTo("\"Mixed Case\"");
        assertThat(SqlIdentifiers.quote(DatabaseProduct.POSTGRESQL, "x\"; DROP TABLE t; --"))
                .isEqualTo("\"x\"\"; DROP TABLE t; --\"");
        assertThat(SqlIdentifiers.quote(DatabaseProduct.MYSQL, "a\"b"))
                .as("別の種類の引用符はそのまま")
                .isEqualTo("`a\"b`");
    }

    @Test
    @DisplayName("empty identifiers and identifiers containing NUL are rejected")
    void rejectsEmptyAndNul() {
        assertThatThrownBy(() -> SqlIdentifiers.quote(DatabaseProduct.MYSQL, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifiers.quote(DatabaseProduct.MYSQL, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifiers.quote(DatabaseProduct.POSTGRESQL, "a\0b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("only names in the list of names read from the database can be quoted")
    void onlyKnownNames() {
        List<String> known = List.of("customers", "Orders");

        assertThat(SqlIdentifiers.quoteKnown(DatabaseProduct.POSTGRESQL, "Orders", known))
                .isEqualTo("\"Orders\"");
        assertThatThrownBy(() -> SqlIdentifiers.quoteKnown(DatabaseProduct.POSTGRESQL, "orders", known))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifiers.quoteKnown(DatabaseProduct.MYSQL, "x`; DROP TABLE t", known))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<String> identifiers() {
        // 引用符・空白・セミコロン・改行・日本語などを多めに混ぜた任意の文字列（NUL と空は除く）。
        Arbitrary<Character> symbols = Arbitraries.of('`', '"', '\'', ';', ' ', '-', '/', '*', '\n', '\\', '表');
        Arbitrary<Character> any = Arbitraries.chars().filter(c -> c != '\0');
        return Arbitraries.frequencyOf(Tuple.of(3, symbols), Tuple.of(2, any))
                .list()
                .ofMinSize(1)
                .ofMaxSize(40)
                .map(chars -> {
                    StringBuilder builder = new StringBuilder();
                    chars.forEach(builder::append);
                    return builder.toString();
                });
    }

    @Property
    @Label("a quoted identifier never lets any character escape the quotes and restores the original name")
    void quotedIdentifierStaysInside(@ForAll("identifiers") String identifier, @ForAll DatabaseProduct product) {
        String quoted = SqlIdentifiers.quote(product, identifier);
        char quote = product.identifierQuote();

        assertThat(quoted.charAt(0)).isEqualTo(quote);
        assertThat(quoted.charAt(quoted.length() - 1)).isEqualTo(quote);
        // 内側を左から読み、引用符は必ず2つ続く（1つだけの引用符＝識別子の終わりは、最後の文字にしか現れない）。
        String inner = quoted.substring(1, quoted.length() - 1);
        StringBuilder restored = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == quote) {
                assertThat(i + 1).as("引用符が1つだけで、識別子の外に出る").isLessThan(inner.length());
                assertThat(inner.charAt(i + 1)).isEqualTo(quote);
                i++;
            }
            restored.append(c);
        }
        assertThat(restored.toString()).isEqualTo(identifier);
    }
}
