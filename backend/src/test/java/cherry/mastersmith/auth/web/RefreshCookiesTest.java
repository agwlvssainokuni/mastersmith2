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
package cherry.mastersmith.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.RefreshTokenValue;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshCookiesTest {

    private final RefreshCookies cookies = new RefreshCookies();

    @Test
    @DisplayName("the issued cookie carries the attributes and the path of the session API")
    void issuedAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.issue(response, new RefreshTokenValue("token-value"), Duration.ofHours(24));

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("mastersmith_refresh=token-value")
                .contains("Max-Age=86400")
                .contains("Path=/api/auth/session")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
    }

    @Test
    @DisplayName("clearing uses the same name and path with Max-Age=0")
    void clearMatchesIssue() {
        MockHttpServletResponse issued = new MockHttpServletResponse();
        MockHttpServletResponse cleared = new MockHttpServletResponse();

        cookies.issue(issued, new RefreshTokenValue("token-value"), Duration.ofHours(24));
        cookies.clear(cleared);

        assertThat(cleared.getHeader("Set-Cookie"))
                .startsWith("mastersmith_refresh=")
                .contains("Max-Age=0")
                .contains("Path=/api/auth/session")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
        assertThat(cleared.getHeader("Set-Cookie")).doesNotContain("token-value");
    }

    @Test
    @DisplayName("the token value is read from the request cookie")
    void reads() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("mastersmith_refresh", "token-value"));

        assertThat(cookies.read(request)).isEqualTo(new RefreshTokenValue("token-value"));
    }

    @Test
    @DisplayName("a missing, empty or unrelated cookie reads as null")
    void readsNothing() {
        MockHttpServletRequest empty = new MockHttpServletRequest();
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie("mastersmith_refresh", ""));
        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setCookies(new Cookie("other", "x"));

        assertThat(cookies.read(empty)).isNull();
        assertThat(cookies.read(blank)).isNull();
        assertThat(cookies.read(other)).isNull();
    }
}
