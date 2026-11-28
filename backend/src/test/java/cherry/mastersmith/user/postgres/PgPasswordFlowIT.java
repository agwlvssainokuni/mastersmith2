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
package cherry.mastersmith.user.postgres;

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

/** パスワードの変更の代表の流れ（BR1.1 の 2。期待は {@code MePasswordApiIT}）。 */
@InternalDbPostgresIT
class PgPasswordFlowIT {

    private static final String NEW_PASSWORD = "新しいパスワード-000000";

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    private static String body(String current, String next, String confirmation) {
        return PortableFixture.json(
                Map.of("currentPassword", current, "newPassword", next, "newPasswordConfirmation", confirmation));
    }

    @Test
    @DisplayName("a member changes the password with 204, then logs in only with the new password")
    void changes() {
        PortableFixture.Account member = fixture.createUser(false);
        String token = fixture.login(member);

        HttpResponse<String> response =
                fixture.send("POST", "/api/me/password", token, body(member.password(), NEW_PASSWORD, NEW_PASSWORD));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(fixture.loginResponse(member.email(), NEW_PASSWORD).statusCode())
                .isEqualTo(200);
        assertThat(fixture.loginResponse(member.email(), member.password()).statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName(
            "a wrong current password is 400 PASSWORD_CURRENT_MISMATCH and a too short new password is 400 VALIDATION_FAILED")
    void failures() {
        PortableFixture.Account member = fixture.createUser(false);
        String token = fixture.login(member);

        HttpResponse<String> mismatch = fixture.send(
                "POST", "/api/me/password", token, body("wrong-" + member.password(), NEW_PASSWORD, NEW_PASSWORD));
        HttpResponse<String> tooShort = fixture.send(
                "POST", "/api/me/password", token, body(member.password(), "a".repeat(11), "a".repeat(11)));

        PortableFixture.assertProblem(mismatch, 400, "PASSWORD_CURRENT_MISMATCH");
        assertThat(HttpTestClient.json(mismatch)).doesNotContainKey("fieldErrors");
        PortableFixture.assertProblem(tooShort, 400, "VALIDATION_FAILED");
        assertThat(HttpTestClient.json(tooShort).get("fieldErrors"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains(Map.of("field", "newPassword", "reason", "TOO_SHORT"));
        assertThat(fixture.loginResponse(member.email(), member.password()).statusCode())
                .isEqualTo(200);
    }
}
