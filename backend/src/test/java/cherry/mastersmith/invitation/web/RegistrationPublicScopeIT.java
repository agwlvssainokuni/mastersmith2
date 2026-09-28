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
package cherry.mastersmith.invitation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.invitation.testsupport.InvitationApi;
import cherry.mastersmith.invitation.testsupport.InvitationTestProperties;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 登録の完了の公開の範囲の結合テスト（BR9.2、NFR4.2・NFR4.3、ADR-011、{@code security-design.md} 3節、Q3 A・R3）。公開は2つの POST だけで、
 * ほかの道・メソッドは {@code /api/**} の既定のログイン必須のまま。公開の道でも壊れたアクセストークンは既存のとおり 401 になる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationPublicScopeIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        InvitationTestProperties.register(registry, tempDir, 25, "http://localhost:8080");
    }

    @LocalServerPort
    int port;

    @Test
    @DisplayName("the two POSTs without a token are processed by the application (not 401)")
    void postsArePublic() {
        InvitationApi api = new InvitationApi(port);

        HttpResponse<String> verify = api.verify("A".repeat(43));
        HttpResponse<String> complete = api.complete("A".repeat(43), "山田 花子");

        assertThat(verify.statusCode()).isEqualTo(404);
        assertThat(HttpTestClient.json(verify)).containsEntry("code", "REGISTRATION_LINK_INVALID");
        assertThat(complete.statusCode()).isEqualTo(404);
        assertThat(HttpTestClient.json(complete)).containsEntry("code", "REGISTRATION_LINK_INVALID");
    }

    @Test
    @DisplayName("a broken access token on the public POSTs is 401 as before (the token is still read)")
    void brokenTokenIs401() {
        InvitationApi api = new InvitationApi(port);

        assertThat(api.post(InvitationApi.VERIFY, "broken-token", "{\"token\":\"x\"}")
                        .statusCode())
                .isEqualTo(401);
        assertThat(api.post(InvitationApi.COMPLETE, "broken-token", "{\"token\":\"x\"}")
                        .statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("other methods and other paths under /api/registration/ stay login-required (401)")
    void othersStayProtected() {
        InvitationApi api = new InvitationApi(port);
        HttpTestClient client = new HttpTestClient(port);

        assertThat(api.get(InvitationApi.VERIFY, null).statusCode()).isEqualTo(401);
        assertThat(api.post("/api/registration/other", null, "{}").statusCode()).isEqualTo(401);
        assertThat(client.send(client.request(InvitationApi.COMPLETE)
                                .header("Content-Type", "application/json")
                                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                                .build())
                        .statusCode())
                .isEqualTo(401);
    }
}
