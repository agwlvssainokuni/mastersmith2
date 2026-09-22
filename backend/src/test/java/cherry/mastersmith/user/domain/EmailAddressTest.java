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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @Property
    @Label("normalizing twice gives the same value as normalizing once")
    void idempotent(@ForAll String email) {
        String once = EmailAddress.normalize(email);
        assertThat(EmailAddress.normalize(once)).isEqualTo(once);
    }

    @Property
    @Label("surrounding ASCII spaces and upper case letters do not change the normalized value")
    void spacesAndCaseIgnored(@ForAll("emails") String email) {
        assertThat(EmailAddress.normalize("  " + email.toUpperCase(java.util.Locale.ROOT) + "\t"))
                .isEqualTo(EmailAddress.normalize(email));
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> emails() {
        return net.jqwik.api.Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(local -> local + "@example.com");
    }

    @Test
    @DisplayName("upper case and surrounding spaces are removed")
    void normalizesExample() {
        assertThat(EmailAddress.normalize("  Admin@Example.COM ")).isEqualTo("admin@example.com");
        assertThat(EmailAddress.normalize(null)).isNull();
    }

    @Test
    @DisplayName("a full-width space is not removed because only characters up to U+0020 are trimmed")
    void fullWidthSpaceKept() {
        assertThat(EmailAddress.normalize("\u3000a@example.com")).isEqualTo("\u3000a@example.com");
        assertThat(EmailAddress.normalize("   ")).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("malformed addresses are rejected")
    @ValueSource(strings = {"", "admin", "admin@", "@example.com", "a b@example.com", "admin@example"})
    void rejectsMalformed(String email) {
        assertThat(EmailAddress.isValid(email)).isFalse();
    }

    @Test
    @DisplayName("well-formed addresses within 254 characters are accepted")
    void acceptsWellFormed() {
        assertThat(EmailAddress.isValid("管理者@example.co.jp")).isTrue();
        assertThat(EmailAddress.isValid(null)).isFalse();
        assertThat(EmailAddress.isValid("a".repeat(250) + "@b.jp")).isFalse();
    }
}
