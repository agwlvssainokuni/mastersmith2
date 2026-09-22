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

import java.nio.charset.StandardCharsets;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    @DisplayName("eleven characters are too short and twelve are enough")
    void minimumLength() {
        assertThat(PasswordPolicy.hasMinimumLength("a".repeat(11))).isFalse();
        assertThat(PasswordPolicy.hasMinimumLength("a".repeat(12))).isTrue();
    }

    @Test
    @DisplayName("characters are counted as code points")
    void codePoints() {
        assertThat(PasswordPolicy.hasMinimumLength("😀".repeat(12))).isTrue();
        assertThat(PasswordPolicy.hasMinimumLength("😀".repeat(11))).isFalse();
    }

    @Test
    @DisplayName("72 bytes fit and 73 bytes do not")
    void byteBoundary() {
        assertThat(PasswordPolicy.fitsMaxBytes("a".repeat(72))).isTrue();
        assertThat(PasswordPolicy.fitsMaxBytes("a".repeat(73))).isFalse();
    }

    @Test
    @DisplayName("multi-byte characters are counted in UTF-8 bytes")
    void multiByteBoundary() {
        assertThat(PasswordPolicy.fitsMaxBytes("あ".repeat(24))).isTrue();
        assertThat(PasswordPolicy.fitsMaxBytes("あ".repeat(24) + "a")).isFalse();
    }

    @Test
    @DisplayName("creation requires both the minimum length and the byte limit")
    void creationRule() {
        assertThat(PasswordPolicy.isAcceptableForCreation("パスワードは十二文字以上です")).isTrue();
        assertThat(PasswordPolicy.isAcceptableForCreation("short")).isFalse();
        assertThat(PasswordPolicy.isAcceptableForCreation("あ".repeat(25))).isFalse();
    }

    @Property
    @Label("the byte-limit check agrees with the UTF-8 byte count")
    void agreesWithUtf8(@ForAll String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        assertThat(PasswordPolicy.utf8Bytes(password)).isEqualTo(bytes);
        assertThat(PasswordPolicy.fitsMaxBytes(password)).isEqualTo(bytes <= 72);
    }
}
