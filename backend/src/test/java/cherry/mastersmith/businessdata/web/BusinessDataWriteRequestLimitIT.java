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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * 書き込みの本文の大きさ・値の長さ・主キーの長さの上限の境界（Intent 261016-business-data-views の U2、計画の Step 20、NFR4.9、
 * security-design.md の 6節、NFR 要件のレビューの R-04）。PostgreSQL のコンテナ。どの応答にも入れた値が入らないことも確かめる。
 *
 * <p>作成・更新の本文の上限は 4 MiB（4,194,304 バイト）、削除は 64 KB。100 カラムを 4 バイトの文字 10,000 字で埋めた最大の本文
 * （約 4,000,000 バイト）が通ることを確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataWriteRequestLimitIT {

    /** 100 カラムの文字のテーブル。 */
    private static final String WIDE = "wide";

    /** 文字の主キーのテーブル。 */
    private static final String TEXT_KEY = "text_key";

    /** 4 バイトの文字（UTF-8 で 4 バイト、1 コードポイント）。 */
    private static final String FOUR_BYTES = "🍣";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private Actor member;

    private Map<String, Object> definitionId;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        StringBuilder wide = new StringBuilder("CREATE TABLE %s" + WIDE + " (id INTEGER NOT NULL PRIMARY KEY");
        List<DslColumn> wideColumns =
                new ArrayList<>(List.of(column("id").dbType("int4").build()));
        for (int i = 1; i <= 100; i++) {
            wide.append(", ").append(columnName(i)).append(" TEXT NULL");
            wideColumns.add(column(columnName(i)).dbType("text").build());
        }
        sample.addObjects(
                wide.append(')').toString(),
                "CREATE TABLE %s" + TEXT_KEY + " (k VARCHAR(1000) NOT NULL PRIMARY KEY, v TEXT NULL)");
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[0]);
        app.installDsl(sample.model(
                table(WIDE, false, List.of("id"), wideColumns.toArray(DslColumn[]::new)),
                table(
                        TEXT_KEY,
                        false,
                        List.of("k"),
                        column("k").dbType("varchar").length(1000).build(),
                        column("v").dbType("text").build())));
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(sample.schema()), new PermissionValues(MainPermission.FULL, true, true));
        member = app.reader(settings);
        definitionId = app.definitionId(member.token(), sample.schema(), ITEM);
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

    private static String columnName(int index) {
        return String.format("c%03d", index);
    }

    private String definitionJson() {
        return MAPPER.writeValueAsString(definitionId);
    }

    /** 作成の本文を、全体がちょうど {@code bytes} バイトになるよう値の長さで伸ばす。 */
    private String createOfSize(int bytes, String id, String extraValues) {
        String head = "{\"schema\":\"" + sample.schema() + "\",\"table\":\"" + ITEM + "\",\"definitionId\":"
                + definitionJson() + ",\"values\":{\"id\":\"" + id + "\"" + extraValues + "},\"pad\":\"";
        String tail = "\"}";
        int padding = bytes - head.getBytes(StandardCharsets.UTF_8).length - tail.length();
        return head + "x".repeat(padding) + tail;
    }

    @Test
    @DisplayName("a create body of exactly 4 MiB is accepted, one byte more is 413 and 413 comes before 400")
    void createBodyBoundary() {
        String exact = createOfSize(4_194_304, "501", "");
        String over = createOfSize(4_194_305, "502", "");
        String overAndInvalid = createOfSize(4_194_305, "not-a-number", "");

        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(4_194_304);
        HttpResponse<String> accepted = app.postRaw(BusinessDataPaths.ROWS, member.token(), exact);
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(201);
        HttpResponse<String> tooLarge = app.postRaw(BusinessDataPaths.ROWS, member.token(), over);
        assertThat(tooLarge.statusCode()).isEqualTo(413);
        assertThat(HttpTestClient.json(tooLarge)).containsEntry("code", "PAYLOAD_TOO_LARGE");
        assertThat(app.postRaw(BusinessDataPaths.ROWS, member.token(), overAndInvalid)
                        .statusCode())
                .isEqualTo(413);
        assertThat(sample.count(ITEM, "id = 501")).isEqualTo(1);
        assertThat(sample.count(ITEM, "id = 502")).isZero();
    }

    @Test
    @DisplayName("an update body of 4 MiB plus one byte is 413 and a delete body of 64 KB plus one byte is 413")
    void updateAndDeleteBodyBoundary() {
        Map<String, Object> update = sample.body(ITEM);
        update.put("definitionId", definitionId);
        update.put("key", Map.of("id", "1"));
        update.put("values", Map.of("name", "x".repeat(10)));
        update.put("pad", "x".repeat(4_194_305));
        Map<String, Object> deleteExact = sample.body(ITEM);
        deleteExact.put("definitionId", definitionId);
        deleteExact.put("key", Map.of("id", "424242"));
        String deleteHead = MAPPER.writeValueAsString(deleteExact);
        String exactDelete = deleteHead.substring(0, deleteHead.length() - 1) + ",\"pad\":\""
                + "x".repeat(65_536 - deleteHead.length() - 9) + "\"}";

        HttpResponse<String> tooLargeUpdate = app.patch(BusinessDataPaths.ROWS, member.token(), update);
        assertThat(tooLargeUpdate.statusCode()).isEqualTo(413);
        assertThat(exactDelete.getBytes(StandardCharsets.UTF_8)).hasSize(65_536);
        HttpResponse<String> exact = app.postRaw(BusinessDataPaths.DELETE, member.token(), exactDelete);
        assertThat(exact.statusCode()).as("上限ちょうどは受け付け、行が無いので 404").isEqualTo(404);
        HttpResponse<String> over = app.postRaw(
                BusinessDataPaths.DELETE, member.token(), exactDelete.substring(0, exactDelete.length() - 2) + "x\"}");
        assertThat(over.statusCode()).isEqualTo(413);
        assertThat(sample.count(ITEM, "id = 1 AND name = 'xxxxxxxxxx'")).isZero();
    }

    @Test
    @DisplayName("the largest body with 100 columns of 10,000 four-byte characters is created")
    void largestBody() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("id", "1");
        for (int i = 1; i <= 100; i++) {
            values.put(columnName(i), FOUR_BYTES.repeat(10_000));
        }
        Map<String, Object> body = sample.body(WIDE);
        body.put("definitionId", definitionId);
        body.put("values", values);
        String json = MAPPER.writeValueAsString(body);

        HttpResponse<String> response = app.postRaw(BusinessDataPaths.ROWS, member.token(), json);

        assertThat(json.getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThan(4_000_000)
                .isLessThan(4_194_304);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(sample.count(WIDE, "id = 1 AND char_length(c100) = 10000")).isEqualTo(1);
        assertThat(response.body()).doesNotContain(FOUR_BYTES);
    }

    @Test
    @DisplayName("values of 10,000 characters are accepted and 10,001 are 400 TOO_LONG without the value")
    void valueLength() {
        Map<String, Object> exact = sample.body(TEXT_KEY);
        exact.put("definitionId", definitionId);
        exact.put("values", Map.of("k", "v10000", "v", "あ".repeat(10_000)));
        Map<String, Object> over = sample.body(TEXT_KEY);
        over.put("definitionId", definitionId);
        over.put("values", Map.of("k", "v10001", "v", "あ".repeat(10_001)));

        assertThat(app.post(BusinessDataPaths.ROWS, member.token(), exact).statusCode())
                .isEqualTo(201);
        HttpResponse<String> tooLong = app.post(BusinessDataPaths.ROWS, member.token(), over);
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(tooLong))
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("fieldErrors", List.of(Map.of("field", "v", "reason", "TOO_LONG")));
        assertThat(tooLong.body()).doesNotContain("あああ");
        assertThat(sample.count(TEXT_KEY, "k = 'v10001'")).isZero();
    }

    @Test
    @DisplayName("keys of 1,000 characters are accepted and 1,001 are 400 without the key")
    void keyLength() {
        String key = "k".repeat(1_000);
        sample.database()
                .execute("INSERT INTO " + sample.database().q(sample.schema()) + "." + TEXT_KEY + " (k, v) VALUES ('"
                        + key + "', 'x')");
        Map<String, Object> accepted = sample.body(TEXT_KEY);
        accepted.put("definitionId", definitionId);
        accepted.put("key", Map.of("k", key));
        Map<String, Object> rejected = sample.body(TEXT_KEY);
        rejected.put("definitionId", definitionId);
        rejected.put("key", Map.of("k", key + "k"));

        HttpResponse<String> tooLong = app.post(BusinessDataPaths.DELETE, member.token(), rejected);
        HttpResponse<String> deleted = app.post(BusinessDataPaths.DELETE, member.token(), accepted);

        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(HttpTestClient.json(tooLong)).containsEntry("code", "VALIDATION_FAILED");
        assertThat(tooLong.body()).doesNotContain("kkkkkkkkkk");
        assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(200);
        assertThat(sample.count(TEXT_KEY, "char_length(k) = 1000")).isZero();
    }
}
