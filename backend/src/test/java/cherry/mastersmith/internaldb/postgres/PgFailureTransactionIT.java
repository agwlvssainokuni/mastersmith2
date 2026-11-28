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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.internaldb.testsupport.ConflictProbe;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 失敗の後のトランザクション（BR4.1）。PostgreSQL は 1 文の失敗でトランザクションを中断状態（25P02）にするため、失敗した処理は巻き戻され、書きかけが
 * 残らず、失敗の監査は別のトランザクションで残り、次の要求が同じ接続の中断状態に当たらずに通る。
 *
 * <p>(1) ロールの作成の名前の重なり（{@code RoleConflictAuditIT} と同じ経路）: 先の側が確定していない同じ名前を持つ間にアプリが挿入して待ち、先の側が
 * 確定すると一意の違反になって巻き戻り、失敗の監査（別のトランザクション）が残って 409 になる。(2) 2 文目の失敗: DSL の適用（版の挿入の後にプレビューを消す 2 文目）を、
 * この IT の専用のデータベースに置いたトリガーで失敗させ、挿入した版が残らず（件数が元のまま）、トリガーを外すと再試行が通ることを確かめる。
 */
@InternalDbPostgresIT
class PgFailureTransactionIT {

    private static PgApp app;

    private static PortableFixture fixture;

    private ConflictProbe probe;

    @BeforeAll
    static void start() {
        app = PgApp.start();
        fixture = new PortableFixture(app);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @BeforeEach
    void setUp() {
        probe = new ConflictProbe(app.database());
    }

    @AfterEach
    void tearDown() {
        try {
            probe.releaseHolders();
            probe.awaitNoWaiters();
        } finally {
            probe.close();
        }
    }

    private static String createBody(String name) {
        return PortableFixture.json(Map.of("name", name));
    }

    @Test
    @DisplayName(
            "a role create that loses a same-name race is rolled back, audited as a failure in another transaction, and the next requests go on")
    void duplicateNameRace() throws Exception {
        String name = PortableFixture.uniqueName("race");
        String adminToken = fixture.adminToken();
        int failures = fixture.auditCount("ROLE_CREATED", "FAILURE");
        int successes = fixture.auditCount("ROLE_CREATED", "SUCCESS");
        Connection holder = probe.openHolder();
        ConflictProbe.execute(
                holder,
                "INSERT INTO roles (name, name_key, created_at, updated_at) VALUES ('" + name + "', '"
                        + name.toLowerCase(java.util.Locale.ROOT) + "', now(), now())");

        Future<HttpResponse<String>> creating =
                probe.async(() -> fixture.send("POST", "/api/admin/roles", adminToken, createBody(name)));
        probe.awaitWaiting();
        holder.commit();
        HttpResponse<String> response = creating.get(15, TimeUnit.SECONDS);
        probe.release(holder);

        PortableFixture.assertProblem(response, 409, "ROLE_NAME_DUPLICATE");
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM roles WHERE name = '" + name + "'"))
                .as("only the committed row of the other side remains")
                .isEqualTo(1);
        assertThat(fixture.auditCount("ROLE_CREATED", "FAILURE")).isEqualTo(failures + 1);
        assertThat(fixture.auditCount("ROLE_CREATED", "SUCCESS")).isEqualTo(successes);
        assertThat(fixture.send("POST", "/api/admin/roles", adminToken, createBody(PortableFixture.uniqueName("next")))
                        .statusCode())
                .as("the next write on the pool goes on without 25P02")
                .isEqualTo(201);
        assertThat(fixture.send("GET", "/api/admin/roles", adminToken, null).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName(
            "a failure of the second statement of a DSL apply leaves nothing of the first one, and the retry goes on")
    void secondStatementFailure() {
        String previewId = fixture.placePreview(PortableFixture.minimalDsl("atomic_t"));
        long revisions = fixture.queryLong("SELECT COUNT(*) FROM dsl_applied_revisions");
        fixture.executeSql("CREATE FUNCTION pg_it_fail_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                + " RAISE EXCEPTION 'injected failure' USING ERRCODE = 'P0001'; END $$");
        fixture.executeSql("CREATE TRIGGER pg_it_fail_delete BEFORE DELETE ON dsl_previews FOR EACH ROW"
                + " EXECUTE FUNCTION pg_it_fail_delete()");
        HttpResponse<String> failed;
        try {
            failed = fixture.adminCall(
                    "POST", "/api/admin/dsl/apply", PortableFixture.json(Map.of("previewId", previewId)));
        } finally {
            fixture.executeSql("DROP TRIGGER pg_it_fail_delete ON dsl_previews");
            fixture.executeSql("DROP FUNCTION pg_it_fail_delete()");
        }

        assertThat(failed.statusCode()).as(PortableFixture.describe(failed)).isEqualTo(500);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_applied_revisions"))
                .as("the revision inserted by the first statement is rolled back")
                .isEqualTo(revisions);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_previews WHERE preview_id = '" + previewId + "'"))
                .as("the preview is still there")
                .isEqualTo(1);
        assertThat(fixture.adminCall(
                                "POST", "/api/admin/dsl/apply", PortableFixture.json(Map.of("previewId", previewId)))
                        .statusCode())
                .as("the retry goes on after the trigger is dropped")
                .isEqualTo(200);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_applied_revisions"))
                .isEqualTo(revisions + 1);
    }
}
