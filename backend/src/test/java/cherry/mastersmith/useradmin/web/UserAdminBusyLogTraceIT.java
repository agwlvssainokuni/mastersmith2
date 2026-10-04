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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 409 {@code USER_ADMIN_BUSY} のログの結び付き（Intent 261004-safety-carryover の FR3.1）の結合テスト。本番のコードは変えない。
 *
 * <p>{@link UserAdminBusyApiIT} と同じく、別の接続（{@link RowLockHolder}）で対象の行を {@code FOR UPDATE} で持ち続けて排他の待ちの
 * 上限切れを起こす。2つの経路（印を付ける操作で利用者の行を持つ {@code ADMIN_ROWS}、止める操作でリフレッシュトークンの行を持つ
 * {@code REFRESH_TOKEN_ROWS}）で 409 を起こし、変換の境界の WARN（L3: {@code GlobalExceptionHandler}、{@code code} が
 * {@code USER_ADMIN_BUSY}）の各行に、同じ空でない {@code traceId} を持つ排他の失敗の WARN（L4: {@code RowLockFailures} の決まった文、
 * {@code lockKind}・{@code exceptionClass} を持つ）がちょうど1行あることを確かめる。{@code traceId} は応答の {@code traceId} と一致する。
 *
 * <p>既存の {@code UserAdminSecretLeakIT} も、同じ {@code traceId} のアプリの WARN が2行あることを確かめている。このテストは FR3.1 の
 * 形（L3 ごとに L4 がちょうど1行）で確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class UserAdminBusyLogTraceIT {

    private static final String HOLD_USER = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";

    private static final String HOLD_REFRESH_TOKENS =
            "SELECT token_id FROM refresh_tokens WHERE user_id = ? FOR UPDATE";

    /** 変換の境界の WARN を出すロガー（L3）。 */
    private static final String HANDLER_LOGGER = "cherry.mastersmith.common.error.web.GlobalExceptionHandler";

    /** 排他の失敗の WARN の決まった文（L4。{@code RowLockFailures.WARN_MESSAGE}）。 */
    private static final String LOCK_WARN_MESSAGE = "行の排他を取れませんでした";

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
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    private UserAdminApi api;

    private UserAdminFixtures fixtures;

    private String admin;

    @BeforeEach
    void setUp() {
        api = new UserAdminApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        String email = "trace-admin-" + UserAdminFixtures.marker() + "@example.com";
        fixtures.create(email, "結び 管理者", true);
        admin = fixtures.login(email);
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, userId);
    }

    private HttpResponse<String> busy(long target, String action, String holdSql) throws SQLException {
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), holdSql, target)) {
            assertThat(holder.lockedRows()).isGreaterThanOrEqualTo(1);
            return api.operate(admin, target, action);
        }
    }

    @Test
    @DisplayName(
            "each USER_ADMIN_BUSY handler warning has exactly one row-lock warning with the same non-empty traceId")
    void eachBusyWarningHasOneLockWarningWithSameTraceId(CapturedOutput output) throws SQLException {
        long grantTarget = fixtures.create("trace-busy-" + UserAdminFixtures.marker() + "@example.com", "結び 一郎", false);
        long suspendTarget =
                fixtures.create("trace-busy-" + UserAdminFixtures.marker() + "@example.com", "結び 二郎", false);
        fixtures.login(emailOf(suspendTarget));
        assertThat(fixtures.activeRefreshTokens(suspendTarget)).isEqualTo(1);

        int outOffset = output.getOut().length();
        int errOffset = output.getErr().length();
        List<HttpResponse<String>> responses = new ArrayList<>();
        responses.add(busy(grantTarget, "grant-admin", HOLD_USER));
        responses.add(busy(suspendTarget, "suspend", HOLD_REFRESH_TOKENS));
        List<String> expectedLockKinds = List.of("ADMIN_ROWS", "REFRESH_TOKEN_ROWS");

        String logs = output.getOut().substring(outOffset) + output.getErr().substring(errOffset);
        List<Map<String, Object>> records = JsonLogRecords.parse(logs);

        List<Map<String, Object>> l3 = records.stream()
                .filter(record -> "WARN".equals(record.get("level")))
                .filter(record -> HANDLER_LOGGER.equals(record.get("logger")))
                .filter(record -> "USER_ADMIN_BUSY".equals(record.get("code")))
                .toList();
        assertThat(l3).as("送った 409 の数だけ L3 がある").hasSize(responses.size());

        for (int i = 0; i < responses.size(); i++) {
            HttpResponse<String> response = responses.get(i);
            assertThat(response.statusCode()).isEqualTo(409);
            Map<String, Object> body = HttpTestClient.json(response);
            assertThat(body).containsEntry("code", "USER_ADMIN_BUSY");
            String traceId = String.valueOf(body.get("traceId"));
            assertThat(traceId).as("応答の traceId").isNotBlank().isNotEqualTo("null");

            List<Map<String, Object>> sameL3 = l3.stream()
                    .filter(record -> traceId.equals(record.get("traceId")))
                    .toList();
            assertThat(sameL3).as("応答の traceId の L3 がちょうど1行").hasSize(1);

            List<Map<String, Object>> l4 = records.stream()
                    .filter(record -> "WARN".equals(record.get("level")))
                    .filter(record -> LOCK_WARN_MESSAGE.equals(record.get("message")))
                    .filter(record -> traceId.equals(record.get("traceId")))
                    .toList();
            assertThat(l4).as("L3 と同じ traceId の L4 がちょうど1行").hasSize(1);
            assertThat(l4.get(0))
                    .containsEntry("lockKind", expectedLockKinds.get(i))
                    .containsKey("exceptionClass");
        }

        assertThat(l3).as("すべての L3 が空でない traceId を持ち、L4 と結び付く").allSatisfy(record -> {
            Object traceId = record.get("traceId");
            assertThat(traceId).isNotNull();
            assertThat(String.valueOf(traceId)).isNotBlank();
            assertThat(records)
                    .filteredOn(other -> LOCK_WARN_MESSAGE.equals(other.get("message")))
                    .filteredOn(other -> traceId.equals(other.get("traceId")))
                    .hasSize(1);
        });
    }
}
