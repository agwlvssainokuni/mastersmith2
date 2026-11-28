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
package cherry.mastersmith.navigation.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * メニュー（ナビゲーションの木）の代表の流れ（BR1.1 の 8。期待は {@code NavigationApiIT}）。DSL の適用で全体の状態を変えるため、
 * クラスごとのアプリを使う。
 */
@InternalDbPostgresIT
class PgNavigationFlowIT {

    private static PgApp app;

    private static PortableFixture fixture;

    /** DSL を適用する前の、役割の無い利用者の応答の {@code emptyReason}。 */
    private static Object emptyReasonBeforeDsl;

    @BeforeAll
    static void start() {
        app = PgApp.start();
        fixture = new PortableFixture(app);
        PortableFixture.Account early = fixture.createUser(false);
        emptyReasonBeforeDsl = HttpTestClient.json(
                        fixture.send("GET", "/api/me/navigation", fixture.login(early), null))
                .get("emptyReason");
        fixture.applyPreview(fixture.placePreview(PortableFixture.minimalDsl("nav_t")));
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("a member whose work role can read the table sees the menu item with its id, label, icon and table")
    void filteredMenu() {
        PortableFixture.Account member = fixture.createUser(false);
        long roleId = fixture.createRole(PortableFixture.uniqueName("nav"));
        fixture.grantTable(roleId, "public", "nav_t", "READ");
        fixture.assignRoleToUser(roleId, member.userId());

        HttpResponse<String> response = fixture.send("GET", "/api/me/navigation", fixture.login(member), null);

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("emptyReason", null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst())
                .containsEntry("id", "0")
                .containsEntry("navigable", true)
                .containsEntry("label", Map.of("ja", "表", "en", "Table"))
                .containsEntry("table", Map.of("schemaName", "public", "tableName", "nav_t"));
        assertThat(items.getFirst().keySet())
                .containsExactlyInAnyOrder("id", "label", "icon", "table", "navigable", "items", "tableKind");
    }

    @Test
    @DisplayName(
            "no DSL is NOT_CONFIGURED, a member without a work role is NOTHING_VISIBLE, and a request without a token is 401")
    void emptyAndUnauthenticated() {
        PortableFixture.Account member = fixture.createUser(false);

        HttpResponse<String> response = fixture.send("GET", "/api/me/navigation", fixture.login(member), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(response))
                .containsEntry("items", List.of())
                .containsEntry("emptyReason", "NOTHING_VISIBLE");
        assertThat(emptyReasonBeforeDsl).isEqualTo("NOT_CONFIGURED");
        assertThat(fixture.send("GET", "/api/me/navigation", null, null).statusCode())
                .isEqualTo(401);
    }
}
