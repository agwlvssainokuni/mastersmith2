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
package cherry.mastersmith.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.DslFormat;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DSL の JSON Schema の公開の結合テスト（U2 の NFR 設計 6節、Infrastructure Design の決定 D）。
 *
 * <p>今のセキュリティの決まりは {@code /api/**} の外をログインなしで通すため、許す設定は足していない。ここでは、ログインなしで
 * 取れることと、中身が検証に使う同梱の正本と同じことを、実際の番号で待ち受けるアプリで確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DslSchemaPublicationIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    private static String classpathText(String resource) throws IOException {
        try (InputStream in = DslSchemaPublicationIT.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("the JSON schema is served without login and is the same as the bundled original")
    void schemaIsPublic() throws IOException {
        HttpResponse<String> response = new HttpTestClient(port).get(DslFormat.SCHEMA_PUBLIC_PATH);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body()).isEqualTo(classpathText(DslFormat.SCHEMA_RESOURCE));
        assertThat(HttpTestClient.json(response))
                .containsEntry("$schema", "https://json-schema.org/draft/2020-12/schema");
    }

    @Test
    @DisplayName("the served schema carries nosniff and the build copy equals the original")
    void schemaHeadersAndCopy() throws IOException {
        HttpResponse<String> response = new HttpTestClient(port).get(DslFormat.SCHEMA_PUBLIC_PATH);

        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(classpathText("static/dsl/dsl-schema-v1.json")).isEqualTo(classpathText(DslFormat.SCHEMA_RESOURCE));
    }
}
