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
package cherry.mastersmith.dslmanage.web;

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.error.web.GlobalExceptionHandler;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dslmanage.service.DslOperationMetrics;
import cherry.mastersmith.dslmanage.testsupport.DslApi;
import cherry.mastersmith.dslmanage.testsupport.DslAuditRows;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DSL の管理の API の結合テスト（契約 C6。組み込みの H2、対象DB は設定しない）。投入・表示・破棄・ダウンロード・適用・履歴・本文の
 * 上限・監査・ログを、実際の番号で待ち受けるアプリへ HTTP で送って確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AdminAccessTestConfig.class)
class DslAdminApiIT {

    /** 投入の上限（10MB = 10,485,760 バイト）。 */
    private static final int TEN_MB = 10 * 1024 * 1024;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DslReader dslReader;

    @Autowired
    ActiveDslModelProvider activeDslModelProvider;

    @Autowired
    ActiveDslModelHolder activeDslModelHolder;

    private AdminTestUsers users;

    private AdminTestUsers.TestUser admin;

    private DslApi api;

    private DslAuditRows audit;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM dsl_previews");
        jdbc.update("DELETE FROM dsl_applied_revisions");
        activeDslModelHolder.replace(null);
        users = new AdminTestUsers(userAccountService, port);
        admin = users.createAdmin();
        api = new DslApi(port, users.accessToken(admin));
        audit = new DslAuditRows(jdbc);
    }

    private static byte[] sample(String table) {
        return DslYaml.dsl()
                .menu("表", "Table", table)
                .table(table, column("code"), column("name"))
                .bytes();
    }

    /** 検証を通る DSL を、コメントの行で指定のバイト数ちょうどにする。 */
    private static byte[] padded(byte[] dsl, int size) {
        StringBuilder text = new StringBuilder(new String(dsl, StandardCharsets.UTF_8));
        int remaining = size - dsl.length;
        String line = "#" + "x".repeat(998) + "\n";
        while (remaining >= line.length() + 2) {
            text.append(line);
            remaining -= line.length();
        }
        text.append("#").append("y".repeat(remaining - 2)).append("\n");
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        assertThat(bytes).hasSize(size);
        return bytes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    @DisplayName("status and history are empty before anything is placed or applied")
    void emptyState() {
        HttpResponse<String> status = api.get("/status");
        HttpResponse<String> history = api.get("/history");

        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(DslApi.json(status)).containsEntry("applied", null).containsEntry("preview", null);
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(DslApi.jsonList(history)).isEmpty();
    }

    @Test
    @DisplayName("without a token the DSL APIs return 401, and a non-admin gets 403")
    void accessControl() {
        DslApi anonymous = new DslApi(port, null);
        DslApi member = new DslApi(port, users.accessToken(users.createNonAdmin()));

        assertThat(anonymous.get("/status").statusCode()).isEqualTo(401);
        assertThat(anonymous.submit(sample("t"), "PASTE").statusCode()).isEqualTo(401);
        assertThat(anonymous.apply("00000000-0000-0000-0000-000000000000").statusCode())
                .isEqualTo(401);
        assertThat(member.get("/status").statusCode()).isEqualTo(403);
        assertThat(member.submit(sample("t"), "PASTE").statusCode()).isEqualTo(403);
        assertThat(member.generate().statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dsl_previews", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("a valid submission becomes the preview with summary, diff and warnings, and is audited")
    void submitAndShow() {
        byte[] body = sample("dept");

        HttpResponse<String> placed = api.submit(body, "UPLOAD");
        HttpResponse<String> shown = api.get("/preview");
        HttpResponse<String> status = api.get("/status");

        assertThat(placed.statusCode()).isEqualTo(201);
        Map<String, Object> preview = DslApi.json(placed);
        assertThat(preview)
                .containsEntry("dslHash", dslReader.hash(body))
                .containsEntry("source", "UPLOAD")
                .containsKeys("previewId", "at");
        assertThat(map(preview.get("by"))).containsEntry("email", admin.email());
        assertThat(map(preview.get("summary")))
                .containsEntry("tableCount", 1)
                .containsEntry("columnCount", 2)
                .containsEntry("missingDisplayNameTotal", 0);
        assertThat(map(preview.get("diff"))).containsEntry("appliedExists", false);
        assertThat(list(map(preview.get("diff")).get("tables")).getFirst()).containsEntry("change", "ADDED");
        assertThat(list(preview.get("warnings")))
                .singleElement()
                .satisfies(warning -> assertThat(warning)
                        .containsEntry("kind", "TARGET_UNCONFIGURED")
                        .containsEntry("path", null));
        assertThat(shown.statusCode()).isEqualTo(200);
        assertThat(DslApi.json(shown)).containsEntry("previewId", preview.get("previewId"));
        assertThat(map(DslApi.json(status).get("preview"))).containsEntry("previewId", preview.get("previewId"));
        DslAuditRows.Row row = audit.last();
        assertThat(row.eventType()).isEqualTo("DSL_SUBMITTED");
        assertThat(row.result()).isEqualTo("SUCCESS");
        assertThat(row.actorUserId()).isEqualTo(admin.userId());
        assertThat(row.dslHash()).isEqualTo(dslReader.hash(body));
        assertThat(row.dslSource()).isEqualTo("UPLOAD");
        assertThat(row.all()).doesNotContain("code:").doesNotContain("version");
    }

    @Test
    @DisplayName(
            "an invalid submission is 422 DSL_INVALID with localized errors, is not stored and is audited with its kind")
    void invalidSubmission() {
        String previewBefore = api.submitOk(sample("keep"));
        byte[] invalid = DslYaml.dsl()
                .table("t", column("c"))
                .yaml()
                .replace("formPart: text", "formPart: text\n        jdbcUrl: 1")
                .getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> en = api.withLanguage("en").submit(invalid, "PASTE");
        HttpResponse<String> ja = api.submit(invalid, "PASTE");

        assertThat(en.statusCode()).isEqualTo(422);
        Map<String, Object> problem = DslApi.json(en);
        assertThat(problem)
                .containsEntry("code", "DSL_INVALID")
                .containsEntry("total", 1)
                .containsKey("traceId");
        assertThat(list(problem.get("errors"))).singleElement().satisfies(error -> {
            assertThat(error).containsEntry("kind", "SYNTAX").containsKeys("line", "column", "path");
            assertThat((String) error.get("message")).isEqualTo("The item \"jdbcUrl\" is not part of the format.");
        });
        assertThat(ja.body()).contains("書式に無い項目「jdbcUrl」があります。");
        assertThat(en.body() + ja.body())
                .doesNotContain("Exception")
                .doesNotContain("snakeyaml")
                .doesNotContain("networknt")
                .doesNotContain("$.");
        assertThat(DslApi.json(api.get("/preview"))).containsEntry("previewId", previewBefore);
        DslAuditRows.Row row = audit.last();
        assertThat(row.eventType()).isEqualTo("DSL_SUBMISSION_REJECTED");
        assertThat(row.result()).isEqualTo("FAILURE");
        assertThat(row.rejectionKind()).isEqualTo("SYNTAX");
        assertThat(row.dslHash()).isEqualTo(dslReader.hash(invalid));
        assertThat(row.dslSource()).isEqualTo("PASTE");
    }

    @Test
    @DisplayName("the first 100 errors and the total are returned when there are more")
    void errorsAreCutAt100() {
        DslYaml.Column[] columns = new DslYaml.Column[105];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = column("c" + i);
        }
        byte[] invalid = DslYaml.dsl()
                .table("t", columns)
                .yaml()
                .replace("formPart: text", "formPart: text\n        extra: 1")
                .getBytes(StandardCharsets.UTF_8);

        Map<String, Object> problem = DslApi.json(api.submit(invalid, "PASTE"));

        assertThat(problem).containsEntry("total", 105);
        assertThat(list(problem.get("errors"))).hasSize(100);
    }

    @Test
    @DisplayName("a body that is not application/yaml is rejected with 415")
    void unsupportedMediaType() {
        HttpResponse<String> response = api.submit(sample("t"), "PASTE", "text/plain");

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(DslApi.json(response)).containsEntry("code", "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("exactly 10MB is not rejected by size, one byte over is 413 DSL_TOO_LARGE and audited with the actor")
    void tenMegabyteBoundary() {
        byte[] exact = padded(sample("big"), TEN_MB);

        HttpResponse<String> accepted = api.submit(exact, "UPLOAD");
        int rowsBefore = audit.dslRows().size();
        DslApi.RawResponse rejected = api.submitHeadersOnly(TEN_MB + 1L, "PASTE");

        assertThat(accepted.statusCode()).isEqualTo(201);
        assertThat(rejected.status()).as("本文を送らなくても、Content-Length だけで断る").isEqualTo(413);
        assertThat(rejected.body()).contains("\"code\":\"DSL_TOO_LARGE\"");
        assertThat(audit.dslRows()).hasSize(rowsBefore + 1);
        DslAuditRows.Row row = audit.last();
        assertThat(row.eventType()).isEqualTo("DSL_SUBMISSION_REJECTED");
        assertThat(row.rejectionKind()).isEqualTo("SIZE_LIMIT");
        assertThat(row.dslHash()).isNull();
        assertThat(row.dslSource()).isEqualTo("PASTE");
        assertThat(row.actorUserId()).isEqualTo(admin.userId());
    }

    @Test
    @DisplayName("other APIs keep the 1MB limit, and an anonymous oversized submission is 401 before the body is read")
    void otherLimitsAndAnonymous() {
        String bigJson = "{\"previewId\":\"" + "x".repeat(1024 * 1024) + "\"}";

        HttpResponse<String> apply = api.post("/apply", bigJson);
        DslApi.RawResponse anonymous = new DslApi(port, null).submitHeadersOnly(TEN_MB + 1L, "PASTE");

        assertThat(apply.statusCode()).isEqualTo(413);
        assertThat(DslApi.json(apply)).containsEntry("code", "PAYLOAD_TOO_LARGE");
        assertThat(anonymous.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("the downloaded preview is the stored bytes as an attachment and resubmitting it keeps the hash")
    void downloadPreview() {
        byte[] body = sample("dl");
        api.submitOk(body);
        String hash = dslReader.hash(body);

        HttpResponse<byte[]> download = api.getBytes("/preview/download");

        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/yaml"));
        assertThat(download.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"dsl-preview-" + hash.substring(0, 12) + ".yaml\"");
        assertThat(download.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(Arrays.equals(download.body(), body)).isTrue();
        assertThat(DslApi.json(api.submit(download.body(), "UPLOAD"))).containsEntry("dslHash", hash);
    }

    @Test
    @DisplayName("discard removes the preview and is audited; afterwards display, download and discard are 404")
    void discard() {
        api.submitOk(sample("gone"));

        HttpResponse<String> discarded = api.discard();

        assertThat(discarded.statusCode()).isEqualTo(204);
        assertThat(audit.last().eventType()).isEqualTo("DSL_PREVIEW_DISCARDED");
        assertThat(audit.last().dslHash()).isEqualTo(dslReader.hash(sample("gone")));
        for (HttpResponse<String> response :
                List.of(api.discard(), api.get("/preview"), api.get("/preview/download"))) {
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(DslApi.json(response)).containsEntry("code", "DSL_PREVIEW_NOT_FOUND");
        }
    }

    @Test
    @DisplayName("apply switches the applied DSL, removes the preview, adds history and is audited; a stale id is 409")
    void applyAndStale() {
        String first = api.submitOk(sample("a"));
        String second = api.submitOk(sample("b"));

        HttpResponse<String> stale = api.apply(first);
        HttpResponse<String> applied = api.apply(second);

        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(DslApi.json(stale)).containsEntry("code", "DSL_PREVIEW_CHANGED");
        assertThat(applied.statusCode()).isEqualTo(200);
        Map<String, Object> status = DslApi.json(applied);
        assertThat(status).containsEntry("preview", null);
        assertThat(map(status.get("applied"))).containsEntry("dslHash", dslReader.hash(sample("b")));
        assertThat(activeDslModelProvider.current())
                .isInstanceOfSatisfying(
                        ActiveDsl.Present.class,
                        present -> assertThat(present.model().tables()).containsOnlyKeys("b"));
        assertThat(DslApi.jsonList(api.get("/history")))
                .singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("current", true));
        assertThat(audit.last().eventType()).isEqualTo("DSL_APPLIED");
        assertThat(api.apply(second).statusCode()).as("プレビューが無い").isEqualTo(409);
        assertThat(api.apply("not-a-uuid").statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("applying the same content again still adds history, and the history keeps at most 20 revisions")
    void historyLimit() {
        byte[] body = sample("h");
        for (int i = 0; i < 19; i++) {
            api.apply(api.submitOk(body));
        }
        List<Map<String, Object>> nineteen = DslApi.jsonList(api.get("/history"));
        api.apply(api.submitOk(body));
        List<Map<String, Object>> twenty = DslApi.jsonList(api.get("/history"));
        api.apply(api.submitOk(body));
        List<Map<String, Object>> stillTwenty = DslApi.jsonList(api.get("/history"));

        assertThat(nineteen).hasSize(19);
        assertThat(twenty)
                .hasSize(20)
                .extracting(entry -> entry.get("revisionId"))
                .contains(nineteen.getLast().get("revisionId"));
        assertThat(stillTwenty)
                .hasSize(20)
                .extracting(entry -> entry.get("revisionId"))
                .doesNotContain(nineteen.getLast().get("revisionId"));
        assertThat(stillTwenty.stream().filter(entry -> Boolean.TRUE.equals(entry.get("current"))))
                .hasSize(1);
        assertThat(stillTwenty.getFirst()).containsEntry("current", true);
    }

    @Test
    @DisplayName("each operation logs one INFO with the keys, and an expected failure is a WARN without a stack trace")
    void logs() {
        try (LogEvents operations = LogEvents.capture(DslOperationMetrics.class);
                LogEvents errors = LogEvents.capture(GlobalExceptionHandler.class)) {
            api.submitOk(sample("log"));
            api.apply("11111111-1111-1111-1111-111111111111");

            assertThat(operations.list()).hasSize(2);
            Map<String, String> keys = operations.list().getFirst().getKeyValuePairs().stream()
                    .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
            assertThat(keys)
                    .containsEntry("dsl.operation", "submit")
                    .containsEntry("dsl.outcome", "success")
                    .containsEntry("dsl.source", "PASTE")
                    .containsKeys("dsl.durationMs", "dsl.hash");
            assertThat(operations.list().get(1).getKeyValuePairs().toString())
                    .contains("apply")
                    .contains("rejected");
            ILoggingEvent warn = errors.list().getFirst();
            assertThat(warn.getLevel()).isEqualTo(Level.WARN);
            assertThat(warn.getThrowableProxy()).isNull();
            assertThat(operations.list().toString()).doesNotContain("code:").doesNotContain("version: 1");
        }
    }

    @Test
    @DisplayName(
            "loading the schema without a configured target database is 503 TARGET_DB_UNCONFIGURED and keeps the preview")
    void generateWithoutTargetDb() {
        String previewId = api.submitOk(sample("keep"));
        int auditBefore = audit.dslRows().size();

        HttpResponse<String> response = api.generate();

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(DslApi.json(response)).containsEntry("code", "TARGET_DB_UNCONFIGURED");
        assertThat(DslApi.json(api.get("/preview"))).containsEntry("previewId", previewId);
        assertThat(audit.dslRows()).hasSize(auditBefore);
    }
}
