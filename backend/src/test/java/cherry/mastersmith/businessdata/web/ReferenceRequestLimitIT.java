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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_API;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.common.testsupport.HttpTestClient;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 参照先の検索 API の本文の大きさ・値の長さ・ページの番号の上限の境界（Intent 261029-reference-lookup-cleanup の U1、Step 21、BR1.4、
 * NFR1.4、security-design.md 4節）。PostgreSQL のコンテナ。どの誤りの応答にも入れた値が入らないことも確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceRequestLimitIT {

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private Actor member;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        ReferenceTables tables = new ReferenceTables(sample);
        tables.createApi();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target());
        app.installDsl(sample.model(tables.apiTables()));
        member = app.reader(tables.apiSettings(Map.of()));
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

    /** 本文を、全体がちょうど {@code bytes} バイトになるよう知らない項目の詰め物で伸ばす。 */
    private String bodyOfSize(int bytes) {
        String head = "{\"schema\":\"" + sample.schema() + "\",\"table\":\"" + SRC_API
                + "\",\"column\":\"pick\",\"conditions\":[{\"column\":\"s\",\"value\":\"S1\"}],\"page\":1,\"pad\":\"";
        String tail = "\"}";
        int padding = bytes - head.getBytes(StandardCharsets.UTF_8).length - tail.length();
        return head + "x".repeat(padding) + tail;
    }

    @Test
    @DisplayName("a body of exactly 64 KB is accepted and one byte more is 413")
    void bodyBoundary() {
        String exact = bodyOfSize(65_536);
        String over = bodyOfSize(65_537);

        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(65_536);
        HttpResponse<String> accepted = app.postRaw(BusinessDataPaths.REFERENCE_ROWS, member.token(), exact);
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        HttpResponse<String> tooLarge = app.postRaw(BusinessDataPaths.REFERENCE_ROWS, member.token(), over);
        assertThat(tooLarge.statusCode()).isEqualTo(413);
        assertThat(HttpTestClient.json(tooLarge)).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("condition values and label values of 1,000 characters are accepted and 1,001 are 400")
    void valueLength() {
        HttpResponse<String> search1000 = search(List.of(Map.of("column", "s", "value", "あ".repeat(1_000))), 1);
        HttpResponse<String> search1001 = search(List.of(Map.of("column", "s", "value", "あ".repeat(1_001))), 1);
        HttpResponse<String> label1000 = label("あ".repeat(1_000));
        HttpResponse<String> label1001 = label("あ".repeat(1_001));

        assertThat(search1000.statusCode()).as(search1000.body()).isEqualTo(200);
        assertThat(HttpTestClient.json(search1000)).containsEntry("rows", List.of());
        assertThat(label1000.statusCode()).as("形は正しく、行が無いため 403").isEqualTo(403);
        for (HttpResponse<String> tooLong : List.of(search1001, label1001)) {
            assertThat(tooLong.statusCode()).isEqualTo(400);
            assertThat(HttpTestClient.json(tooLong))
                    .containsEntry("code", "VALIDATION_FAILED")
                    .doesNotContainKey("fieldErrors");
            assertThat(tooLong.body()).doesNotContain("あああ");
        }
    }

    @ParameterizedTest(name = "page {0} gives {1}")
    @CsvSource({"0, 400", "1, 200", "1000000, 200", "1000001, 400", "-1, 400", "1.5, 400"})
    @DisplayName("pages 1 to 1,000,000 are accepted and the rest is 400 without field errors")
    void pageBoundary(String page, int status) {
        HttpResponse<String> response =
                search(List.of(Map.of("column", "s", "value", "S1")), new java.math.BigDecimal(page));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        if (status == 400) {
            assertThat(HttpTestClient.json(response)).doesNotContainKey("fieldErrors");
        }
        if ("1000000".equals(page)) {
            assertThat(HttpTestClient.json(response))
                    .containsEntry("rows", List.of())
                    .containsEntry("hasNext", false)
                    .containsEntry("page", 1_000_000);
        }
    }

    private HttpResponse<String> search(List<Map<String, Object>> conditions, Object page) {
        return app.post(
                BusinessDataPaths.REFERENCE_ROWS,
                member.token(),
                sample.body(SRC_API, "column", "pick", "conditions", conditions, "page", page));
    }

    private HttpResponse<String> label(String value) {
        return app.post(
                BusinessDataPaths.REFERENCE_ROWS,
                member.token(),
                sample.body(SRC_API, "column", "pick", "value", value));
    }
}
