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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_MAIN;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_MM;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_HID;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 保存前の参照の確かめの監査（Intent 261029-reference-lookup-cleanup の U2、計画の Step 10、BR5.1〜BR5.3、AC3.3.1〜AC3.3.3）。
 * PostgreSQL のコンテナ。拒否は既存の出来事に結果 {@code FAILURE}・理由 {@code BUSINESS_REFERENCE_NOT_FOUND} で1行（作成は空の
 * detail、更新は主キーだけ）、確かめの読み取りそのものと 400 は残らず、MISMATCH は既存の写しで残ることを確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceWriteAuditIT {

    /** 本文の参照の値の目印（監査に出ない）。 */
    private static final String BODY_MARK = "zz-refa-body-0d5c";

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private Actor member;

    private JdbcTemplate jdbc;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        ReferenceTables tables = new ReferenceTables(sample);
        tables.createWrite();
        tables.createFailing();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[0]);
        app.installDsl(sample.model(tables.failingTables()));
        member = app.reader(tables.writeSettings(Map.of()));
        jdbc = app.bean(JdbcTemplate.class);
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

    private Map<String, Object> body(String table) {
        Map<String, Object> body = sample.body(table);
        body.put("definitionId", app.definitionId(member.token(), sample.schema(), table));
        return body;
    }

    private HttpResponse<String> create(String table, Map<String, String> values) {
        Map<String, Object> body = body(table);
        body.put("values", values);
        return app.post(BusinessDataPaths.ROWS, member.token(), body);
    }

    private HttpResponse<String> update(String id, Map<String, String> values) {
        Map<String, Object> body = body(WS_MAIN);
        body.put("key", Map.of("id", id));
        body.put("values", values);
        return app.patch(BusinessDataPaths.ROWS, member.token(), body);
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private int auditCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type LIKE 'BUSINESS_%'", Integer.class);
    }

    private Map<String, Object> lastAudit() {
        return jdbc.queryForMap("SELECT event_type, result, failure_reason, target_table, detail FROM audit_events"
                + " WHERE event_type LIKE 'BUSINESS_%' ORDER BY audit_event_id DESC LIMIT 1");
    }

    private static void status(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    }

    @Test
    @DisplayName("a create rejection is one FAILURE row with BUSINESS_REFERENCE_NOT_FOUND and an empty detail")
    void createRejection() {
        int before = auditCount();

        status(create(WS_MAIN, values("id", "8001", "ref", BODY_MARK)), 409);

        assertThat(auditCount()).isEqualTo(before + 1);
        assertThat(lastAudit())
                .containsEntry("EVENT_TYPE", "BUSINESS_ROW_CREATED")
                .containsEntry("RESULT", "FAILURE")
                .containsEntry("FAILURE_REASON", "BUSINESS_REFERENCE_NOT_FOUND")
                .containsEntry("TARGET_TABLE", WS_MAIN)
                .containsEntry("DETAIL", null);
    }

    @Test
    @DisplayName("a required reference dead end is audited with the same reason")
    void deadEndRejection() {
        status(create(WS_REQ_HID, values("id", "8002", "memo", "m")), 409);

        assertThat(lastAudit())
                .containsEntry("RESULT", "FAILURE")
                .containsEntry("FAILURE_REASON", "BUSINESS_REFERENCE_NOT_FOUND");
    }

    @Test
    @DisplayName("an update rejection keeps only the primary key of the source row in detail")
    void updateRejection() {
        sample.addObjects("INSERT INTO %s" + WS_MAIN + " (id, ref) VALUES (8003, 'V1')");

        status(update("8003", values("ref", BODY_MARK)), 409);

        assertThat(lastAudit())
                .containsEntry("EVENT_TYPE", "BUSINESS_ROW_UPDATED")
                .containsEntry("FAILURE_REASON", "BUSINESS_REFERENCE_NOT_FOUND")
                .containsEntry("DETAIL", "{\"key\":{\"id\":\"8003\"},\"keyTruncated\":false}");
    }

    @Test
    @DisplayName("checks are not audited, a success with references leaves one success row as before")
    void checksAreNotAudited() {
        int before = auditCount();

        status(create(WS_MAIN, values("id", "8004", "ref", "V1", "num_ref", "7", "pick_ref", "V2")), 201);
        status(create(WS_MAIN, values("id", "8005", "name", "参照なし")), 201);

        assertThat(auditCount()).isEqualTo(before + 2);
        assertThat(lastAudit()).containsEntry("RESULT", "SUCCESS").containsEntry("FAILURE_REASON", null);
    }

    @Test
    @DisplayName("a 400 is not audited and a MISMATCH keeps the existing reason")
    void validationAndMismatch() {
        int before = auditCount();
        status(create(WS_MAIN, values("id", "abc", "ref", "V1")), 400);
        assertThat(auditCount()).isEqualTo(before);

        status(create(WS_MM, values("id", "8006", "m", "x")), 409);
        assertThat(lastAudit())
                .containsEntry("RESULT", "FAILURE")
                .containsEntry("FAILURE_REASON", "BUSINESS_TARGET_DB_MISMATCH");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE detail LIKE ?", Integer.class, "%" + BODY_MARK + "%"))
                .as("参照の値は残らない")
                .isZero();
    }
}
