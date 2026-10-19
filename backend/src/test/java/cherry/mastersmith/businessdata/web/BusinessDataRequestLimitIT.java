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
package cherry.mastersmith.businessdata.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本文の大きさ・値の長さ・ページの番号の上限の境界（NFR4.5、security-design.md の 6節）。PostgreSQL のコンテナ。どの応答にも入れた値が
 * 入らないことも確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataRequestLimitIT {

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private Actor member;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target());
        app.installDsl(sample.model());
        member = app.reader(sample.settings(MainPermission.READ));
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
        } finally {
            sample.stop();
        }
    }

    /** 本文を、全体がちょうど {@code bytes} バイトになるよう詰め物の項目で伸ばす。 */
    private String bodyOfSize(int bytes, String conditionsJson) {
        String head = "{\"schema\":\"" + sample.schema() + "\",\"table\":\"item\",\"conditions\":" + conditionsJson
                + ",\"page\":1,\"size\":20,\"pad\":\"";
        String tail = "\"}";
        int padding = bytes - head.getBytes(StandardCharsets.UTF_8).length - tail.length();
        return head + "x".repeat(padding) + tail;
    }

    @Test
    @DisplayName("a body of exactly 64 KB is accepted and one byte more is 413")
    void bodyBoundary() {
        String exact = bodyOfSize(65_536, "[]");
        String over = bodyOfSize(65_537, "[]");

        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(65_536);
        assertThat(app.postRaw(BusinessDataPaths.SEARCH, member.token(), exact).statusCode())
                .isEqualTo(200);
        HttpResponse<String> tooLarge = app.postRaw(BusinessDataPaths.SEARCH, member.token(), over);
        assertThat(tooLarge.statusCode()).isEqualTo(413);
        assertThat(HttpTestClient.json(tooLarge)).containsEntry("code", "PAYLOAD_TOO_LARGE");
        assertThat(app.postRaw(BusinessDataPaths.DEFINITION, member.token(), over)
                        .statusCode())
                .isEqualTo(413);
        assertThat(app.postRaw(BusinessDataPaths.GET, member.token(), over).statusCode())
                .isEqualTo(413);
    }

    @Test
    @DisplayName("an over-sized body with an invalid value is 413 before 400")
    void payloadTooLargeComesFirst() {
        String over = bodyOfSize(65_537, "[{\"column\":\"id\",\"value\":\"not-a-number\"}]");

        assertThat(app.postRaw(BusinessDataPaths.SEARCH, member.token(), over).statusCode())
                .isEqualTo(413);
    }

    @Test
    @DisplayName("search and key values of 1,000 characters are accepted and 1,001 characters are 400 INVALID_VALUE")
    void valueLength() {
        String accepted = "あ".repeat(1_000);
        String rejected = "あ".repeat(1_001);

        assertThat(search(List.of(Map.of("column", "name", "value", accepted))).statusCode())
                .isEqualTo(200);
        HttpResponse<String> tooLong = search(List.of(Map.of("column", "name", "value", rejected)));
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(tooLong))
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("definitionChanged", true)
                .containsEntry("fieldErrors", List.of(Map.of("field", "name", "reason", "INVALID_VALUE")));
        assertThat(tooLong.body()).doesNotContain("あああ");

        HttpResponse<String> key = app.post(
                BusinessDataPaths.GET, member.token(), sample.body("item", "key", Map.of("id", "1".repeat(1_001))));
        assertThat(key.statusCode()).isEqualTo(400);
        assertThat(key.body()).doesNotContain("1111111111");
    }

    @Test
    @DisplayName("page 1,000,000 is accepted with an empty page and 1,000,001 is 400 without field errors")
    void pageBoundary() {
        HttpResponse<String> last =
                app.post(BusinessDataPaths.SEARCH, member.token(), sample.body("item", "page", 1_000_000, "size", 100));
        HttpResponse<String> over =
                app.post(BusinessDataPaths.SEARCH, member.token(), sample.body("item", "page", 1_000_001, "size", 100));

        assertThat(last.statusCode()).as(last.body()).isEqualTo(200);
        assertThat(HttpTestClient.json(last)).containsEntry("total", 3).containsEntry("items", List.of());
        assertThat(over.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(over))
                .doesNotContainKey("fieldErrors")
                .containsEntry("code", "VALIDATION_FAILED");
    }

    private HttpResponse<String> search(List<Map<String, Object>> conditions) {
        return app.post(
                BusinessDataPaths.SEARCH,
                member.token(),
                sample.body(
                        "item",
                        "conditions",
                        conditions,
                        "definitionId",
                        Map.of("dslHash", BusinessDataDslFixture.OTHER_HASH, "workRoleId", 1)));
    }
}
