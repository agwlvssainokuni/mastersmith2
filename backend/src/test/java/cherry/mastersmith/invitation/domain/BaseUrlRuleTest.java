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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** ベース URL の決まりの単体テスト（BR1.3、NFR1.4）。 */
class BaseUrlRuleTest {

    @ParameterizedTest
    @CsvSource({
        "http://localhost:8080, http://localhost:8080",
        "https://example.com, https://example.com",
        "https://example.com/app/, https://example.com/app",
        "'  https://example.com//  ', https://example.com",
        "HTTPS://example.com/, HTTPS://example.com"
    })
    @DisplayName("http and https absolute URLs are usable with the trailing slash removed")
    void usable(String raw, String expected) {
        BaseUrlRule.Check check = BaseUrlRule.evaluate(raw);

        assertThat(check.usable()).contains(expected);
        assertThat(check.invalid()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("a missing or blank value is not usable and is not warned about")
    void missing(String raw) {
        BaseUrlRule.Check check = BaseUrlRule.evaluate(raw);

        assertThat(check.usable()).isEmpty();
        assertThat(check.invalid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ftp://example.com",
                "example.com",
                "/relative",
                "http://",
                "http:/example.com",
                "https://example.com/?a=1",
                "https://example.com/#x",
                "http://user@example.com",
                "http://exa mple.com"
            })
    @DisplayName("malformed values are not usable and are warned about")
    void invalid(String raw) {
        BaseUrlRule.Check check = BaseUrlRule.evaluate(raw);

        assertThat(check.usable()).isEmpty();
        assertThat(check.invalid()).isTrue();
    }

    @Test
    @DisplayName("the string form of the result hides the value")
    void toStringHidesValue() {
        assertThat(BaseUrlRule.evaluate("https://secret-host.example.com").toString())
                .doesNotContain("secret-host");
    }
}
