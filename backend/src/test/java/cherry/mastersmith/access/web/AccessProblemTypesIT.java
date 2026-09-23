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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** U3 の問題の種類が U1 の説明ページに日英で出ることの結合テスト（BR6.1、U1 の決まり 5.14）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessProblemTypesIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("each U3 problem type is published in Japanese and English")
    @CsvSource(
            delimiter = '|',
            value = {
                "access-denied|ACCESS_DENIED|403|この操作を行う権限がありません|Access denied",
                "request-rejected|REQUEST_REJECTED|400|要求を受け付けられません|Request rejected"
            })
    void published(String slug, String code, int status, String japanese, String english) {
        HttpTestClient client = new HttpTestClient(port);

        HttpResponse<String> ja = client.get("/api/problems/" + slug);
        HttpResponse<String> en = client.get("/api/problems/" + slug, "Accept-Language", "en-US,en;q=0.9");

        assertThat(ja.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(ja))
                .containsEntry("code", code)
                .containsEntry("status", status)
                .containsEntry("title", japanese);
        assertThat(HttpTestClient.json(ja).get("description").toString()).doesNotContain("/api/");
        assertThat(HttpTestClient.json(en)).containsEntry("title", english);
        assertThat(HttpTestClient.json(en)).containsKey("resolution");
    }
}
