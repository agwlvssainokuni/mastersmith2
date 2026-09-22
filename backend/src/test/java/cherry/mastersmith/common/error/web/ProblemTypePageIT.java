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
package cherry.mastersmith.common.error.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 問題の種類の説明ページの結合テスト（BR5.11〜BR5.13、BR5.15、BR6.3）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProblemTypePageIT {

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path, String... headers) {
        return new HttpTestClient(port).get(path, headers);
    }

    @Test
    @DisplayName("page is available without login and returns JSON in Japanese by default")
    void jsonInJapaneseByDefault() {
        HttpResponse<String> response = get("/api/problems/validation-failed");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.headers().firstValue("Content-Language")).contains("ja");
        assertThat(HttpTestClient.json(response))
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("status", 400)
                .containsEntry("title", "入力の検証に失敗しました")
                .containsEntry("language", "ja");
    }

    @Test
    @DisplayName("Accept-Language en returns English and unsupported languages fall back to Japanese")
    void languageSelection() {
        assertThat(HttpTestClient.json(get("/api/problems/not-found", "Accept-Language", "en-US,en;q=0.9")))
                .containsEntry("title", "Not found")
                .containsEntry("language", "en");
        assertThat(HttpTestClient.json(get("/api/problems/not-found", "Accept-Language", "fr-FR")))
                .containsEntry("title", "見つかりません");
    }

    @Test
    @DisplayName("browser Accept header prefers HTML")
    void htmlWhenPreferred() {
        HttpResponse<String> response =
                get("/api/problems/payload-too-large", "Accept", BROWSER_ACCEPT, "Accept-Language", "en");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(
                        type -> assertThat(type).startsWith("text/html").containsIgnoringCase("charset=UTF-8"));
        assertThat(response.body()).contains("<html lang=\"en\">").contains("Payload too large");
    }

    @Test
    @DisplayName("wildcard or JSON-first Accept headers return JSON")
    void jsonWhenNotPreferringHtml() {
        assertThat(get("/api/problems/not-found", "Accept", "*/*").headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(get("/api/problems/not-found", "Accept", "application/json, text/html")
                        .headers()
                        .firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
    }

    @Test
    @DisplayName("undefined slug returns 404 NOT_FOUND whose type points to the not-found page")
    void undefinedSlug() {
        HttpResponse<String> response = get("/api/problems/no-such-problem", "Accept", BROWSER_ACCEPT);

        assertThat(response.statusCode()).isEqualTo(404);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body)
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("type", "http://localhost:" + port + "/api/problems/not-found");
    }

    @Test
    @DisplayName("request values such as the slug are never embedded in the page")
    void requestValuesNotEmbedded() {
        HttpResponse<String> page =
                get("/api/problems/not-found?note=%3Cscript%3Ealert(1)%3C/script%3E", "Accept", BROWSER_ACCEPT);
        assertThat(page.body())
                .doesNotContain("<script")
                .doesNotContain("note=")
                .doesNotContain("alert(1)");

        HttpResponse<String> unknown =
                get("/api/problems/%3Cscript%3Ealert(1)%3C%2Fscript%3E", "Accept", BROWSER_ACCEPT);
        assertThat(unknown.body()).doesNotContain("<script");
    }

    @Test
    @DisplayName("page responses carry the Content-Security-Policy header")
    void cspPresent() {
        assertThat(get("/api/problems/not-found", "Accept", BROWSER_ACCEPT)
                        .headers()
                        .firstValue("Content-Security-Policy"))
                .hasValueSatisfying(
                        csp -> assertThat(csp).contains("default-src 'self'").contains("script-src 'self'"));
        assertThat(get("/api/problems/not-found").headers().firstValue("Content-Security-Policy"))
                .isPresent();
    }

    @Test
    @DisplayName("every problem type defined by U1 has a page in both languages")
    void everyU1TypeHasPage() {
        for (String slug : new String[] {
            "validation-failed",
            "malformed-request",
            "not-found",
            "method-not-allowed",
            "not-acceptable",
            "payload-too-large",
            "unsupported-media-type",
            "internal-error"
        }) {
            assertThat(get("/api/problems/" + slug).statusCode()).as(slug).isEqualTo(200);
            assertThat(get("/api/problems/" + slug, "Accept-Language", "en").statusCode())
                    .as(slug)
                    .isEqualTo(200);
        }
    }
}
