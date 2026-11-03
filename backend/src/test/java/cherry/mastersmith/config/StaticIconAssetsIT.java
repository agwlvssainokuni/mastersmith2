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
package cherry.mastersmith.config;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
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
 * アイコン・favicon の素材の配信の仕組みの結合テスト（U6 の SD4.2 の L1）。
 * クラスパスの {@code static/} はテスト用の見本のため、本物の素材ではなく「ログインなしで画像として返る」仕組みを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.test-fixture.public-api=true")
class StaticIconAssetsIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    private HttpResponse<String> getUnauthenticated(String path) {
        HttpResponse<String> response = new HttpTestClient(port).get(path);
        // 実際の Content-Type を出力に残す（コード生成の記録に使う）
        System.out.println("StaticIconAssetsIT " + path + " Content-Type="
                + response.headers().firstValue("Content-Type"));
        return response;
    }

    private static void assertServedAsImage(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(v -> assertThat(v).startsWith("image/"));
        assertThat(response.body()).doesNotStartWithIgnoringCase("<!doctype html");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().allValues("Cache-Control")).containsExactly("no-cache");
    }

    @Test
    @DisplayName("favicon.ico is served to an unauthenticated request as an image")
    void faviconIcoIsServedAsImage() {
        assertServedAsImage(getUnauthenticated("/favicon.ico"));
    }

    @Test
    @DisplayName("favicon.svg is served to an unauthenticated request as image/svg+xml")
    void faviconSvgIsServedAsSvg() {
        HttpResponse<String> response = getUnauthenticated("/favicon.svg");

        assertServedAsImage(response);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(v -> assertThat(v).startsWith("image/svg+xml"));
    }

    @Test
    @DisplayName("a missing asset path silently falls back to index.html (why the dist and WAR checks are needed)")
    void missingAssetFallsBackToIndexHtml() {
        HttpResponse<String> response = getUnauthenticated("/missing-icon.ico");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(v -> assertThat(v).startsWith("text/html"));
        assertThat(response.body()).contains("test-spa-index");
    }
}
