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
package cherry.mastersmith.auth.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgSharedApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ログイン・トークンの更新・ログアウトの代表の流れ（BR1.1 の 1。期待は {@code LoginApiIT}・{@code TokenApiIT}・{@code LogoutApiIT}）。 */
@InternalDbPostgresIT
class PgAuthFlowIT {

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    @Test
    @DisplayName("login, refresh rotation and logout work on PostgreSQL with the same responses as on H2")
    void loginRefreshAndLogout() {
        PortableFixture.Account account = fixture.createUser(false);

        HttpResponse<String> login = fixture.loginResponse(account.email(), account.password());

        assertThat(login.statusCode()).isEqualTo(200);
        Map<String, Object> body = HttpTestClient.json(login);
        assertThat(body).containsOnlyKeys("accessToken", "expiresAt", "user");
        assertThat(body.get("user"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsOnlyKeys("email", "admin", "displayName", "language", "theme", "fontSize")
                .containsEntry("admin", false)
                .containsEntry("language", "ja")
                .containsEntry("theme", "system")
                .containsEntry("fontSize", "md");
        assertThat(PortableFixture.setCookieLine(login))
                .contains("Path=/api/auth/session")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Max-Age=86400");

        String cookie = PortableFixture.cookieValue(login);
        HttpResponse<String> refreshed = fixture.refresh(cookie, fixture.origin());
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(refreshed)).containsKeys("accessToken", "expiresAt", "user");
        String rotated = PortableFixture.cookieValue(refreshed);
        assertThat(rotated).isNotEqualTo(cookie);
        PortableFixture.assertProblem(fixture.refresh(cookie, fixture.origin()), 401, "REFRESH_FAILED");

        assertThat(fixture.logout(rotated, fixture.origin()).statusCode()).isEqualTo(204);
        PortableFixture.assertProblem(fixture.refresh(rotated, fixture.origin()), 401, "REFRESH_FAILED");
    }

    @Test
    @DisplayName(
            "a wrong password, an empty email and a missing origin are rejected with the same status and code as on H2")
    void failures() {
        PortableFixture.Account account = fixture.createUser(false);
        HttpResponse<String> login = fixture.loginResponse(account.email(), account.password());

        PortableFixture.assertProblem(
                fixture.loginResponse(account.email(), "wrong-" + account.password()), 401, "AUTHENTICATION_FAILED");
        PortableFixture.assertProblem(fixture.loginResponse("", account.password()), 400, "VALIDATION_FAILED");
        PortableFixture.assertProblem(
                fixture.refresh(PortableFixture.cookieValue(login), null), 403, "ORIGIN_NOT_ALLOWED");
        PortableFixture.assertProblem(fixture.refresh(null, fixture.origin()), 401, "REFRESH_FAILED");
    }
}
