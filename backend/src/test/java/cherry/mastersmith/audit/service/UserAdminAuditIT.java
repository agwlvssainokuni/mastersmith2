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
package cherry.mastersmith.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.access.testsupport.AdminTestUsers.TestUser;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.service.OperationResult;
import cherry.mastersmith.useradmin.service.UserAdminService;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の管理の操作の監査の結合テスト（Intent 260930-user-admin の U3、契約 C6、BR6.1〜BR6.4、NFR9.3、AC2.1.3〜AC2.1.5・
 * AC3.1.4・AC3.1.6・AC4.1.6）。team.md の必須のテスト（管理の操作の監査）にあたる。
 *
 * <p>5つの操作の成功と、業務の拒否（{@code USER_NOT_FOUND}・{@code SELF_OPERATION}・{@code TARGET_SUSPENDED}・{@code NO_CHANGE}・
 * {@code LAST_ACTIVE_ADMIN}）と確かめ直しの {@code NOT_ADMIN} の行の項目を確かめる。残さない場合（一覧・氏名と言語・BUSY・入力の
 * 誤り・401・認可の入口の 403）は、管理の操作の行が増えない。API の1件ずつでは起きない {@code LAST_ACTIVE_ADMIN}・{@code NOT_ADMIN}
 * は業務処理を直接呼ぶ（{@code UserAdminOperationsIT} と同じ考え方）。テストの手伝いは監査と共通・認証・アクセスのものだけを使う。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class UserAdminAuditIT {

    private static final String PATH = "/api/admin/users/";

    private static final String TRACE_ID = "6e0c63257de34c926f9efcd03899b5f7";

    private static final String USER_AGENT = "user-admin-audit-IT";

    private static final String USER_ADMIN_TYPES = "('USER_ADMIN_GRANTED', 'USER_ADMIN_REVOKED', 'USER_SUSPENDED',"
            + " 'USER_RESUMED', 'LOGIN_FAILURES_RESET')";

    private static final String ROW = "SELECT event_type, result, failure_reason, actor_user_id, target_user_id,"
            + " target_invitation_id, entered_email, source_ip, user_agent, request_path, trace_id, occurred_at"
            + " FROM audit_events WHERE event_type IN " + USER_ADMIN_TYPES + " ORDER BY audit_event_id";

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
    RefreshTokenRevocationService revocationService;

    @Autowired
    UserAdminService userAdminService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private HttpTestClient client;

    private AdminTestUsers users;

    private TestUserSuspension suspension;

    private TestUser admin;

    private String adminToken;

    @BeforeEach
    void setUp() {
        client = new HttpTestClient(port);
        users = new AdminTestUsers(userAccountService, port);
        suspension = new TestUserSuspension(
                new TransactionTemplate(transactionManager), userAccountService, revocationService);
        admin = users.createAdmin();
        adminToken = users.accessToken(admin);
    }

    private HttpResponse<String> operate(String accessToken, String userId, String action, String... headers) {
        HttpRequest.Builder builder = client.request(PATH + userId + "/" + action)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return client.send(builder.build());
    }

    private HttpResponse<String> operate(long userId, String action) {
        return operate(adminToken, String.valueOf(userId), action);
    }

    private List<Map<String, Object>> userAdminRows() {
        return jdbc.queryForList(ROW);
    }

    private List<Map<String, Object>> rowsAfter(int before) {
        List<Map<String, Object>> rows = userAdminRows();
        return new ArrayList<>(rows.subList(before, rows.size()));
    }

    private int allRows() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    private void lockState(long userId, int failures, OffsetDateTime lockedUntil) {
        jdbc.update(
                "MERGE INTO login_attempt_states (subject_id, consecutive_failures, locked_until) KEY (subject_id)"
                        + " VALUES (?, ?, ?)",
                userId,
                failures,
                lockedUntil);
    }

    private void demoteAllAdmins() {
        List<Long> admins = jdbc.queryForList("SELECT user_id FROM users WHERE admin_flag = TRUE", Long.class);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> admins.forEach(id -> userAccountService.setAdmin(id, false)));
    }

    private static void assertRow(
            Map<String, Object> row, String eventType, String result, String reason, long actor, long target) {
        assertThat(row)
                .containsEntry("EVENT_TYPE", eventType)
                .containsEntry("RESULT", result)
                .containsEntry("FAILURE_REASON", reason)
                .containsEntry("ACTOR_USER_ID", actor)
                .containsEntry("TARGET_USER_ID", target)
                .containsEntry("TARGET_INVITATION_ID", null)
                .containsEntry("ENTERED_EMAIL", null)
                .containsEntry("REQUEST_PATH", null);
    }

    @Test
    @DisplayName("each successful operation leaves one SUCCESS row with actor, target, sender, trace id and time")
    void successes() {
        long target = users.createNonAdmin().userId();
        lockState(
                target,
                5,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusMinutes(15));
        int before = userAdminRows().size();
        String traceparent = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

        for (String action : List.of("grant-admin", "revoke-admin", "suspend", "resume", "reset-login-failures")) {
            assertThat(operate(adminToken, String.valueOf(target), action, "traceparent", traceparent)
                            .statusCode())
                    .as(action)
                    .isEqualTo(204);
        }

        List<Map<String, Object>> rows = rowsAfter(before);
        assertThat(rows).hasSize(5);
        List<String> types = List.of(
                "USER_ADMIN_GRANTED", "USER_ADMIN_REVOKED", "USER_SUSPENDED", "USER_RESUMED", "LOGIN_FAILURES_RESET");
        for (int i = 0; i < types.size(); i++) {
            Map<String, Object> row = rows.get(i);
            assertRow(row, types.get(i), "SUCCESS", null, admin.userId(), target);
            assertThat(row)
                    .containsEntry("SOURCE_IP", "127.0.0.1")
                    .containsEntry("USER_AGENT", USER_AGENT)
                    .containsEntry("TRACE_ID", TRACE_ID);
            assertThat(((OffsetDateTime) row.get("OCCURRED_AT")).toInstant()).isEqualTo(clock.instant());
        }
    }

    @Test
    @DisplayName("business rejections leave a FAILURE row with the reason and the requested id, even an unknown one")
    void businessRejections() {
        TestUser member = users.createNonAdmin();
        TestUser suspendedMember = users.createNonAdmin();
        suspension.suspend(suspendedMember.userId());
        int before = userAdminRows().size();

        HttpResponse<String> unknown = operate(Long.MAX_VALUE, "suspend");
        operate(admin.userId(), "revoke-admin");
        operate(suspendedMember.userId(), "grant-admin");
        operate(member.userId(), "resume");

        List<Map<String, Object>> rows = rowsAfter(before);
        assertThat(rows).hasSize(4);
        assertRow(rows.get(0), "USER_SUSPENDED", "FAILURE", "USER_NOT_FOUND", admin.userId(), Long.MAX_VALUE);
        assertRow(rows.get(1), "USER_ADMIN_REVOKED", "FAILURE", "SELF_OPERATION", admin.userId(), admin.userId());
        assertRow(
                rows.get(2),
                "USER_ADMIN_GRANTED",
                "FAILURE",
                "TARGET_SUSPENDED",
                admin.userId(),
                suspendedMember.userId());
        assertRow(rows.get(3), "USER_RESUMED", "FAILURE", "NO_CHANGE", admin.userId(), member.userId());
        assertThat(rows.get(0))
                .as("監査の行とエラー応答のトレースIDが一致する（同じ要求のアプリの記録）")
                .containsEntry("TRACE_ID", HttpTestClient.json(unknown).get("traceId"));
    }

    @Test
    @DisplayName("LAST_ACTIVE_ADMIN and NOT_ADMIN are recorded when the service refuses for them")
    void lastAdminAndNotAdmin() {
        demoteAllAdmins();
        TestUser only = users.createAdmin();
        TestUser suspendedAdmin = users.createAdmin();
        suspension.suspend(suspendedAdmin.userId());
        TestUser member = users.createNonAdmin();
        TestUser target = users.createNonAdmin();
        RequestOrigin origin = new RequestOrigin("192.0.2.80", "direct-IT", null);
        int before = userAdminRows().size();

        assertThat(userAdminService.revokeAdmin(suspendedAdmin.userId(), origin, only.userId()))
                .isInstanceOf(OperationResult.Rejected.class);
        assertThat(userAdminService.grantAdmin(member.userId(), origin, target.userId()))
                .isEqualTo(new OperationResult.OperatorNotAdmin());

        List<Map<String, Object>> rows = rowsAfter(before);
        assertThat(rows).hasSize(2);
        assertRow(
                rows.get(0),
                "USER_ADMIN_REVOKED",
                "FAILURE",
                "LAST_ACTIVE_ADMIN",
                suspendedAdmin.userId(),
                only.userId());
        assertRow(rows.get(1), "USER_ADMIN_GRANTED", "FAILURE", "NOT_ADMIN", member.userId(), target.userId());
        assertThat(rows.get(1)).containsEntry("SOURCE_IP", "192.0.2.80").containsEntry("USER_AGENT", "direct-IT");
    }

    @Test
    @DisplayName("listing, profile changes, BUSY, input errors, 401 and gate 403 leave no user admin row")
    void notRecorded() throws SQLException {
        TestUser member = users.createNonAdmin();
        String memberToken = users.accessToken(member);
        long target = users.createNonAdmin().userId();
        int before = userAdminRows().size();
        int all = allRows();

        assertThat(client.get("/api/admin/users?q=abc", "Authorization", "Bearer " + adminToken)
                        .statusCode())
                .isEqualTo(200);
        assertThat(client.send(client.request(PATH + target + "/profile")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Content-Type", "application/json")
                                .PUT(HttpRequest.BodyPublishers.ofString(
                                        "{\"displayName\":\"監査 なし\",\"language\":\"en\"}"))
                                .build())
                        .statusCode())
                .isEqualTo(204);
        assertThat(operate(adminToken, "abc", "suspend").statusCode()).isEqualTo(400);
        try (RowLockHolder holder = RowLockHolder.hold(
                TestDatabase.url(tempDir), "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", target)) {
            assertThat(operate(target, "suspend").statusCode()).isEqualTo(409);
        }
        assertThat(operate(null, String.valueOf(target), "suspend").statusCode())
                .isEqualTo(401);
        assertThat(operate(memberToken, String.valueOf(target), "suspend").statusCode())
                .isEqualTo(403);

        assertThat(userAdminRows()).hasSize(before);
        List<String> added = jdbc.queryForList(
                "SELECT event_type || ' ' || failure_reason FROM audit_events ORDER BY audit_event_id OFFSET ? ROWS",
                String.class,
                all);
        assertThat(added)
                .as("認可の入口は既存のアクセスの拒否だけが残る")
                .containsExactly("ACCESS_DENIED TOKEN_MISSING", "ACCESS_DENIED NOT_ADMIN");
    }
}
