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

/** 表示の設定の代表の流れ（BR1.1 の 10。期待は {@code MePreferencesApiIT}）。 */
@InternalDbPostgresIT
class PgPreferencesFlowIT {

    private static PortableFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new PortableFixture(PgSharedApp.get());
    }

    @Test
    @DisplayName("a member reads the stored values, saves new ones, and later reads and logins return the saved values")
    void readsAndSaves() {
        PortableFixture.Account member = fixture.createUser(false);
        String token = fixture.login(member);

        HttpResponse<String> before = fixture.send("GET", "/api/me/preferences", token, null);
        assertThat(before.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(before))
                .containsOnlyKeys("displayName", "language", "theme", "fontSize", "displayTimeZone")
                .containsEntry("language", "ja")
                .containsEntry("theme", "system")
                .containsEntry("fontSize", "md")
                .containsEntry("displayTimeZone", null);

        HttpResponse<String> saved = fixture.send(
                "PUT",
                "/api/me/preferences",
                token,
                PortableFixture.json(
                        Map.of("displayName", " 山田 花子　", "language", "en", "theme", "dark", "fontSize", "lg")));

        Map<String, Object> expected =
                Map.of("displayName", "山田 花子", "language", "en", "theme", "dark", "fontSize", "lg");
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(saved))
                .containsOnlyKeys("displayName", "language", "theme", "fontSize", "displayTimeZone")
                .containsAllEntriesOf(expected)
                .containsEntry("displayTimeZone", null);
        assertThat(HttpTestClient.json(fixture.send("GET", "/api/me/preferences", token, null)))
                .containsAllEntriesOf(expected);
        HttpResponse<String> login = fixture.loginResponse(member.email(), member.password());
        assertThat(HttpTestClient.json(login).get("user"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(expected);
    }

    @Test
    @DisplayName(
            "an invalid value is 400 VALIDATION_FAILED and a request without a token is 401 AUTHENTICATION_REQUIRED")
    void failures() {
        PortableFixture.Account member = fixture.createUser(false);
        String token = fixture.login(member);

        HttpResponse<String> invalid = fixture.send(
                "PUT",
                "/api/me/preferences",
                token,
                PortableFixture.json(Map.of("displayName", "名前", "language", "xx", "theme", "dark", "fontSize", "lg")));

        PortableFixture.assertProblem(invalid, 400, "VALIDATION_FAILED");
        PortableFixture.assertProblem(
                fixture.send("GET", "/api/me/preferences", null, null), 401, "AUTHENTICATION_REQUIRED");
        assertThat(HttpTestClient.json(fixture.send("GET", "/api/me/preferences", token, null)))
                .containsEntry("language", "ja");
    }
}
