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
package cherry.mastersmith.access.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 既定でログイン必須になった {@code /api/**} と、公開する範囲の結合テスト（BR1.2〜BR1.5、NFR3.2、NFR3.8）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiDefaultAccessIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    private HttpTestClient client() {
        return new HttpTestClient(port);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("the public list of U1 stays reachable without login")
    @ValueSource(strings = {"/actuator/health", "/api/problems/not-found", "/api/problems/access-denied"})
    void publicListOfU1(String path) {
        assertThat(client().get(path).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("the public list of U2 stays reachable without login")
    @ValueSource(strings = {"/api/auth/login", "/api/auth/session/refresh", "/api/auth/session/logout"})
    void publicListOfU2(String path) {
        HttpResponse<String> response = client().get(path);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "METHOD_NOT_ALLOWED");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("every other /api/ path requires login")
    @ValueSource(strings = {"/api/no-such-api", "/api/users/42", "/api/test-fixture/number", "/api/auth", "/api"})
    void everyOtherApiRequiresLogin(String path) {
        HttpResponse<String> response = client().get(path);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("the screen is still served without login")
    void theScreenStaysPublic() {
        assertThat(client().get("/").statusCode()).isEqualTo(200);
        assertThat(client().get("/admin").statusCode()).isEqualTo(200);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Actuator endpoints other than health stay unreachable")
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/metrics"})
    void onlyHealthIsExposed(String path) {
        HttpResponse<String> response = client().get(path);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "NOT_FOUND");
    }
}
