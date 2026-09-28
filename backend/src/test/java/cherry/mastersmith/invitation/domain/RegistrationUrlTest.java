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
package cherry.mastersmith.invitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 招待の URL の単体テスト（BR3.4・BR3.5、NFR1.4・NFR1.5）。 */
class RegistrationUrlTest {

    private final InvitationToken token = InvitationToken.generate(new SecureRandom());

    @Test
    @DisplayName("the URL is the base URL, /register#token= and the token")
    void builds() {
        assertThat(RegistrationUrl.of("https://example.com/app", token).value())
                .isEqualTo("https://example.com/app/register#token=" + token.value());
    }

    @Test
    @DisplayName("the string form hides the URL and the token")
    void toStringHidesValue() {
        RegistrationUrl url = RegistrationUrl.of("http://localhost:8080", token);

        assertThat(url.toString()).doesNotContain(token.value()).doesNotContain("localhost");
    }

    @Test
    @DisplayName("a missing base URL or token is an unexpected error")
    void requiresValues() {
        assertThatThrownBy(() -> RegistrationUrl.of(null, token)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RegistrationUrl.of("http://localhost", null)).isInstanceOf(NullPointerException.class);
    }
}
