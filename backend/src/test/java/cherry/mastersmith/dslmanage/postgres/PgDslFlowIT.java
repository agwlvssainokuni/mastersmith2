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
package cherry.mastersmith.dslmanage.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DSL の読み込みと適用の代表の流れ（BR1.1 の 9。期待は {@code DslAdminApiIT}）。適用は全体の状態を変えるため、クラスごとのアプリを使う。
 */
@InternalDbPostgresIT
class PgDslFlowIT {

    private static PgApp app;

    private static PortableFixture fixture;

    @BeforeAll
    static void start() {
        app = PgApp.start();
        fixture = new PortableFixture(app);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private static HttpResponse<String> submit(String token, byte[] body) {
        HttpRequest.Builder builder = app.client()
                .request("/api/admin/dsl/preview?source=PASTE")
                .header("Content-Type", "application/yaml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return app.client().send(builder.build());
    }

    @Test
    @DisplayName("a valid submission becomes the preview, is applied, and the status and the history show it")
    void placesAndApplies() {
        assertThat(HttpTestClient.json(fixture.adminCall("GET", "/api/admin/dsl/status", null)))
                .containsEntry("applied", null)
                .containsEntry("preview", null)
                .containsEntry("appliedUnreadable", false);
        int submitted = fixture.auditCount("DSL_SUBMITTED", "SUCCESS");
        int applied = fixture.auditCount("DSL_APPLIED", "SUCCESS");

        HttpResponse<String> placed = submit(fixture.adminToken(), PortableFixture.minimalDsl("dept"));

        assertThat(placed.statusCode()).isEqualTo(201);
        Map<String, Object> preview = HttpTestClient.json(placed);
        assertThat(preview).containsEntry("source", "PASTE").containsKeys("previewId", "dslHash", "at", "summary");
        assertThat(preview.get("summary"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("schemaCount", 1)
                .containsEntry("tableCount", 1)
                .containsEntry("columnCount", 2);
        HttpResponse<String> apply = fixture.adminCall(
                "POST", "/api/admin/dsl/apply", PortableFixture.json(Map.of("previewId", preview.get("previewId"))));
        assertThat(apply.statusCode()).isEqualTo(200);

        Map<String, Object> status = HttpTestClient.json(fixture.adminCall("GET", "/api/admin/dsl/status", null));
        assertThat(status.get("applied")).isNotNull();
        assertThat(status.get("preview")).isNull();
        assertThat(fixture.adminCall("GET", "/api/admin/dsl/history", null).statusCode())
                .isEqualTo(200);
        assertThat(fixture.auditCount("DSL_SUBMITTED", "SUCCESS")).isEqualTo(submitted + 1);
        assertThat(fixture.auditCount("DSL_APPLIED", "SUCCESS")).isEqualTo(applied + 1);
    }

    @Test
    @DisplayName("an invalid DSL is 422 DSL_INVALID, a non-admin is 403 and a request without a token is 401")
    void failures() {
        PortableFixture.Account member = fixture.createUser(false);
        byte[] invalid = "version: 2\nmenus: []\nschemas: 1\n".getBytes(StandardCharsets.UTF_8);

        PortableFixture.assertProblem(submit(fixture.adminToken(), invalid), 422, "DSL_INVALID");
        assertThat(submit(fixture.login(member), PortableFixture.minimalDsl("dept"))
                        .statusCode())
                .isEqualTo(403);
        assertThat(submit(null, PortableFixture.minimalDsl("dept")).statusCode())
                .isEqualTo(401);
    }
}
