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
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.tableWithForeignKeys;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.businessdata.domain.AuditDetailBuilder;
import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 業務データの書き込みの監査（Intent 261016-business-data-views の U2、計画の Step 22、BR11.1〜BR11.6、AC3.4.1〜AC3.4.4・AC3.4.6〜
 * AC3.4.8、NFR4.14）。PostgreSQL のコンテナ。確定の段の時間切れの {@code UNKNOWN} は {@code BusinessDataWriteTimeoutIT} が確かめる。
 *
 * <p>監査の行は内部DB を直接読んで確かめる。{@code detail} は主キーだけ（主キー以外の値・前後の値を持たない）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込みを先、コンテナの実行環境の確かめを後にする（ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
@SuppressWarnings("unchecked")
class BusinessDataWriteAuditIT {

    /** 一意・外部キー・CHECK・DSL にだけあるカラムの表。 */
    private static final String AUDITED = "audited";

    /** 主キーの無い表（⑥ の確かめ）。 */
    private static final String NO_KEY = "nokey";

    /** ビュー（作成の ⑥ の確かめ）。 */
    private static final String VIEW = "item_view";

    /** DSL の主キーが3つの文字のカラムの表（DB の制約は無い。detail の上限の確かめ）。 */
    private static final String LONG_KEY = "longkey";

    /** 主キー以外の値（監査に残らないことを確かめる架空の値）。 */
    private static final String SECRET_VALUE = "監査に出ない値-4417";

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private JdbcTemplate jdbc;

    private FailingAuditEventRepository failing;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        sample.addObjects(
                "CREATE TABLE %s" + AUDITED + " (id INTEGER NOT NULL PRIMARY KEY, code VARCHAR(20) NULL UNIQUE,"
                        + " item_id INTEGER NULL REFERENCES %s" + ITEM + " (id), qty INTEGER NULL CHECK (qty >= 0),"
                        + " v VARCHAR(50) NULL)",
                "CREATE TABLE %s" + NO_KEY + " (txt VARCHAR(20) NULL)",
                "CREATE VIEW %s" + VIEW + " AS SELECT id, name FROM %s" + ITEM,
                "CREATE TABLE %s" + LONG_KEY
                        + " (a VARCHAR(1000) NOT NULL, b VARCHAR(1000) NOT NULL, c VARCHAR(1000) NOT NULL,"
                        + " v VARCHAR(20) NULL)");
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[] {
            FailingAuditEventRepositoryConfig.class
        });
        app.installDsl(sample.model(
                tableWithForeignKeys(
                        AUDITED,
                        List.of("id"),
                        List.of(new DslForeignKey(List.of("item_id"), ITEM, List.of("id"))),
                        column("id").dbType("int4").build(),
                        column("code").length(20).build(),
                        column("item_id").dbType("int4").build(),
                        column("qty").dbType("int4").build(),
                        column("v").length(50).build(),
                        // DB に無いカラム（409 MISMATCH の確かめ）。
                        column("ghost").length(20).build()),
                table(NO_KEY, false, List.of(), column("txt").length(20).build()),
                table(
                        VIEW,
                        true,
                        List.of("id"),
                        column("id").dbType("int4").build(),
                        column("name").build()),
                table(
                        LONG_KEY,
                        false,
                        List.of("a", "b", "c"),
                        column("a").length(1000).build(),
                        column("b").length(1000).build(),
                        column("c").length(1000).build(),
                        column("v").length(20).build())));
        jdbc = app.bean(JdbcTemplate.class);
        failing = app.bean(FailingAuditEventRepository.class);
        failing.mode(FailingAuditEventRepositoryConfig.Mode.NONE);
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

    private Map<PermissionTarget, PermissionValues> settings(MainPermission main, boolean create, boolean delete) {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(sample.schema()), new PermissionValues(main, create, delete));
        return settings;
    }

    private Actor writer() {
        return app.reader(settings(MainPermission.FULL, true, true));
    }

    private Map<String, Object> body(Actor actor, String table) {
        Map<String, Object> body = sample.body(table);
        body.put("definitionId", app.definitionId(actor.token(), sample.schema(), ITEM));
        return body;
    }

    private HttpResponse<String> create(Actor actor, String table, Map<String, String> values) {
        Map<String, Object> body = body(actor, table);
        body.put("values", values);
        return app.post(BusinessDataPaths.ROWS, actor.token(), body);
    }

    private HttpResponse<String> update(
            Actor actor, String table, Map<String, String> key, Map<String, String> values) {
        Map<String, Object> body = body(actor, table);
        body.put("key", key);
        body.put("values", values);
        return app.patch(BusinessDataPaths.ROWS, actor.token(), body);
    }

    private HttpResponse<String> delete(Actor actor, String table, Map<String, String> key) {
        Map<String, Object> body = body(actor, table);
        body.put("key", key);
        return app.post(BusinessDataPaths.DELETE, actor.token(), body);
    }

    private int auditCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type LIKE 'BUSINESS_%'", Integer.class);
    }

    private Map<String, Object> lastAudit() {
        return jdbc.queryForMap("SELECT event_type, result, failure_reason, actor_user_id, work_role_id, target_schema,"
                + " target_table, detail FROM audit_events WHERE event_type LIKE 'BUSINESS_%'"
                + " ORDER BY audit_event_id DESC LIMIT 1");
    }

    private static void status(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    }

    @Test
    @DisplayName("successful create, update and delete leave the actor, work role, table and only the key in detail")
    void successes() {
        Actor actor = writer();
        Object workRole = app.definitionId(actor.token(), sample.schema(), ITEM).get("workRoleId");

        status(create(actor, AUDITED, Map.of("id", "11", "v", SECRET_VALUE)), 201);
        Map<String, Object> created = lastAudit();
        status(update(actor, AUDITED, Map.of("id", "11"), Map.of("v", SECRET_VALUE + "2")), 200);
        Map<String, Object> updated = lastAudit();
        status(delete(actor, AUDITED, Map.of("id", "11")), 200);
        Map<String, Object> deleted = lastAudit();

        assertThat(created)
                .containsEntry("EVENT_TYPE", "BUSINESS_ROW_CREATED")
                .containsEntry("RESULT", "SUCCESS")
                .containsEntry("FAILURE_REASON", null)
                .containsEntry("ACTOR_USER_ID", actor.userId())
                .containsEntry("WORK_ROLE_ID", ((Number) workRole).longValue())
                .containsEntry("TARGET_SCHEMA", sample.schema())
                .containsEntry("TARGET_TABLE", AUDITED)
                .containsEntry("DETAIL", "{\"key\":{\"id\":\"11\"},\"keyTruncated\":false}");
        assertThat(updated)
                .containsEntry("EVENT_TYPE", "BUSINESS_ROW_UPDATED")
                .containsEntry("RESULT", "SUCCESS")
                .containsEntry("DETAIL", "{\"key\":{\"id\":\"11\"},\"keyTruncated\":false}");
        assertThat(deleted)
                .containsEntry("EVENT_TYPE", "BUSINESS_ROW_DELETED")
                .containsEntry("RESULT", "SUCCESS")
                .containsEntry("DETAIL", "{\"key\":{\"id\":\"11\"},\"keyTruncated\":false}");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE detail LIKE ?",
                        Integer.class,
                        "%" + SECRET_VALUE + "%"))
                .as("主キー以外の値は残らない")
                .isZero();
    }

    @Test
    @DisplayName("the failures of BR11.3 are recorded with their reasons")
    void recordedFailures() {
        Actor actor = writer();
        sample.database()
                .execute("INSERT INTO " + sample.database().q(sample.schema()) + "." + AUDITED
                        + " (id, code) VALUES (21, 'DUP')");

        Map<String, String> expected = new LinkedHashMap<>();
        status(update(actor, AUDITED, Map.of("id", "999"), Map.of("v", "x")), 404);
        expected.put("BUSINESS_ROW_UPDATED", "BUSINESS_ROW_NOT_FOUND");
        assertFailure(expected);
        status(create(actor, AUDITED, Map.of("id", "21")), 409);
        expected.put("BUSINESS_ROW_CREATED", "BUSINESS_DUPLICATE_VALUE");
        assertFailure(expected);
        status(create(actor, AUDITED, Map.of("id", "22", "item_id", "999")), 409);
        expected.put("BUSINESS_ROW_CREATED", "BUSINESS_REFERENCE_NOT_FOUND");
        assertFailure(expected);
        status(create(actor, AUDITED, Map.of("id", "23", "qty", "-1")), 409);
        expected.put("BUSINESS_ROW_CREATED", "BUSINESS_SAVE_REJECTED");
        assertFailure(expected);
        status(create(actor, AUDITED, Map.of("id", "24", "ghost", "x")), 409);
        expected.put("BUSINESS_ROW_CREATED", "BUSINESS_TARGET_DB_MISMATCH");
        assertFailure(expected);
        status(delete(actor, ITEM, Map.of("id", "1")), 200);
        status(delete(actor, ITEM, Map.of("id", "1")), 404);
        expected.put("BUSINESS_ROW_DELETED", "BUSINESS_ROW_NOT_FOUND");
        assertFailure(expected);
    }

    private void assertFailure(Map<String, String> expected) {
        Map<String, Object> audit = lastAudit();
        String type = (String) audit.get("EVENT_TYPE");
        assertThat(audit).containsEntry("RESULT", "FAILURE").containsEntry("FAILURE_REASON", expected.get(type));
        expected.remove(type);
    }

    @Test
    @DisplayName("401, 403, 400, unknown tables, an old definition and a missing setting leave no audit row")
    void notRecorded() {
        Actor actor = writer();
        Actor reader = app.reader(settings(MainPermission.READ, false, false));
        int before = auditCount();

        status(app.post(BusinessDataPaths.ROWS, null, sample.body(AUDITED)), 401);
        status(create(reader, AUDITED, Map.of("id", "31")), 403);
        status(create(actor, AUDITED, Map.of("id", "not-a-number")), 400);
        Map<String, Object> unknown = body(actor, "nothing");
        unknown.put("values", Map.of("id", "1"));
        status(app.post(BusinessDataPaths.ROWS, actor.token(), unknown), 404);
        Map<String, Object> stale = sample.body(AUDITED);
        stale.put("definitionId", Map.of("dslHash", "e".repeat(64)));
        stale.put("values", Map.of("id", "32"));
        status(app.post(BusinessDataPaths.ROWS, actor.token(), stale), 409);

        assertThat(auditCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("the key rule rejection is recorded only for those holding the permission of the operation")
    void keyRuleOnlyForHolders() {
        Actor holder = writer();
        Actor createOnly = app.reader(settings(MainPermission.READ, true, false));
        Actor fullWithoutDelete = app.reader(settings(MainPermission.FULL, false, false));
        Actor reader = app.reader(settings(MainPermission.READ, false, false));

        int start = auditCount();
        status(create(holder, VIEW, Map.of("id", "41")), 409);
        status(update(holder, NO_KEY, Map.of("txt", "a"), Map.of("txt", "b")), 409);
        status(delete(holder, NO_KEY, Map.of("txt", "a")), 409);
        int afterHolder = auditCount();
        status(create(createOnly, VIEW, Map.of("id", "42")), 409);
        int afterCreateOnly = auditCount();
        status(update(reader, NO_KEY, Map.of("txt", "a"), Map.of("txt", "b")), 409);
        status(delete(fullWithoutDelete, NO_KEY, Map.of("txt", "a")), 409);
        status(create(fullWithoutDelete, VIEW, Map.of("id", "43")), 409);

        assertThat(afterHolder - start).as("作成・更新・削除の補助権限を持つ人").isEqualTo(3);
        assertThat(afterCreateOnly - afterHolder).as("CREATE を持つ人の作成").isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT failure_reason FROM audit_events WHERE event_type LIKE 'BUSINESS_%'"
                                + " ORDER BY audit_event_id DESC LIMIT 4",
                        String.class))
                .containsOnly("BUSINESS_ROW_OP_NOT_ALLOWED");
        assertThat(auditCount() - afterCreateOnly).as("その操作の権限を持たない人は残さない").isZero();
    }

    @Test
    @DisplayName("a detail of exactly 16,384 characters is kept and one more character is cut with keyTruncated")
    void detailBoundary() {
        Actor actor = writer();
        // 制御の文字は JSON で 6 字（\\u0001）になる。a・b を 1,000 字ずつにし、c で全体をちょうど 16,384 字に合わせる。
        String control = "\u0001".repeat(1_000);
        int base = AuditDetailBuilder.build(key(control, control, "")).json().length();
        int rest = AuditDetailBuilder.MAX_LENGTH - base;
        int sixes = rest / 6;
        String exactC = "\u0001".repeat(sixes) + "x".repeat(rest - sixes * 6);
        assertThat(AuditDetailBuilder.build(key(control, control, exactC)).json())
                .hasSize(16_384);
        String overC = exactC + "y";
        String table = sample.database().q(sample.schema()) + "." + LONG_KEY;
        sample.database()
                .execute(
                        "INSERT INTO " + table + " (a, b, c, v) VALUES (repeat(chr(1), 1000), repeat(chr(1), 1000), '"
                                + exactC.replace("\u0001", "' || chr(1) || '") + "', 'e')",
                        "INSERT INTO " + table + " (a, b, c, v) VALUES (repeat(chr(1), 1000), repeat(chr(1), 1000), '"
                                + overC.replace("\u0001", "' || chr(1) || '") + "', 'o')");

        status(update(actor, LONG_KEY, key(control, control, exactC), Map.of("v", "E")), 200);
        Map<String, Object> exact = lastAudit();
        status(update(actor, LONG_KEY, key(control, control, overC), Map.of("v", "O")), 200);
        Map<String, Object> over = lastAudit();

        assertThat((String) exact.get("DETAIL")).hasSize(16_384).endsWith("\"keyTruncated\":false}");
        assertThat((String) over.get("DETAIL")).hasSizeLessThanOrEqualTo(16_384).endsWith("\"keyTruncated\":true}");
    }

    private static Map<String, String> key(String a, String b, String c) {
        Map<String, String> key = new LinkedHashMap<>();
        key.put("a", a);
        key.put("b", b);
        key.put("c", c);
        return key;
    }

    @Test
    @DisplayName("a failing audit write keeps the operation successful and the ERROR has no detail, key or cause")
    void auditWriteFailure(CapturedOutput output) {
        Actor actor = writer();
        failing.mode(FailingAuditEventRepositoryConfig.Mode.APPEND_FAILURE);
        HttpResponse<String> response;
        try {
            response = create(actor, AUDITED, Map.of("id", "51", "v", SECRET_VALUE));
        } finally {
            failing.mode(FailingAuditEventRepositoryConfig.Mode.NONE);
        }

        status(response, 201);
        assertThat(sample.count(AUDITED, "id = 51")).as("操作は成功のまま").isEqualTo(1);
        List<Map<String, Object>> errors = JsonLogRecords.parse(output.getAll()).stream()
                .filter(r -> "ERROR".equals(r.get("level")))
                .filter(r -> "BUSINESS_ROW_CREATED".equals(r.get("auditEventType")))
                .toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst())
                .containsEntry("keyColumnCount", 1)
                .containsEntry("keyTruncated", false)
                .containsEntry("targetTable", AUDITED)
                .containsKey("exceptionType")
                .doesNotContainKeys("detail", "exception", "stack_trace");
        assertThat(output.getAll()).doesNotContain(SECRET_VALUE).doesNotContain("\"51\"");
        assertThat(HttpTestClient.json(response)).containsKey("key");
    }
}
