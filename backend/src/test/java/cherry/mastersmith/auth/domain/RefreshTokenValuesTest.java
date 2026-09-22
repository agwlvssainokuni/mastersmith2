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
package cherry.mastersmith.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenValuesTest {

    @Test
    @DisplayName("a generated value encodes 32 random bytes in URL-safe Base64 without padding")
    void lengthAndAlphabet() {
        String value = RefreshTokenValues.generate().value();

        assertThat(value).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(value)).hasSize(32);
    }

    @Test
    @DisplayName("generated values differ from each other")
    void unique() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            values.add(RefreshTokenValues.generate().value());
        }
        assertThat(values).hasSize(100);
    }

    @Test
    @DisplayName("the hash is 32 bytes and the same value gives the same hash")
    void deterministicHash() {
        RefreshTokenValue value = RefreshTokenValues.generate();

        assertThat(RefreshTokenValues.hash(value)).hasSize(32).isEqualTo(RefreshTokenValues.hash(value));
    }

    @Test
    @DisplayName("different values give different hashes")
    void differentHashes() {
        assertThat(RefreshTokenValues.hash(new RefreshTokenValue("a")))
                .isNotEqualTo(RefreshTokenValues.hash(new RefreshTokenValue("b")));
    }

    @Test
    @DisplayName("the hash matches the known SHA-256 of the value")
    void knownHash() {
        assertThat(java.util.HexFormat.of().formatHex(RefreshTokenValues.hash(new RefreshTokenValue("abc"))))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
