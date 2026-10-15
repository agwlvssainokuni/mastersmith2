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
package cherry.mastersmith.role.web;

import static cherry.mastersmith.role.testsupport.RoleApi.entry;
import static cherry.mastersmith.role.testsupport.RoleApi.saveBody;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.UncommittedWrite;
import cherry.mastersmith.role.service.RoleTransferSlot;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * {@code ROLE_BUSY} のログ（{@code observability-design.md} 3節、NFR5.3、BR8.3・BR12.3）の結合テスト。ロールの行と名前の鍵の待ちの上限切れを、
 * 別の接続で行を持ち続ける形で起こし、応答の {@code traceId} ごとに、排他の種類と例外のクラスの名前だけを持つ WARN がちょうど1行あること、
 * 名前・例外の文・スタックトレースが無いことを確かめる（group の {@code GroupBusyLogIT} と同じ形）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class RoleBusyLogTraceIT {

    /** 排他の失敗の WARN の決まった文（{@code RowLockFailures.WARN_MESSAGE}）。 */
    private static final String LOCK_WARN_MESSAGE = "行の排他を取れませんでした";

    /** WARN に出てよい項目（ログの共通の項目と、排他の種類・例外のクラスの名前）。 */
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "timestamp", "level", "logger", "thread", "message", "traceId", "spanId", "lockKind", "exceptionClass");

    @Autowired
    RoleTransferSlot slot;

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
    ActiveDslModelHolder holder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    private RoleApi api;

    private RoleFixtures fixtures;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    @Test
    @DisplayName("each ROLE_BUSY has exactly one lock warning with its trace id, lock kind and exception class only")
    void oneWarningPerBusy(CapturedOutput output) throws Exception {
        String rowName = RoleFixtures.uniqueName("ログ 行");
        long rowRole = fixtures.role(rowName);
        String keyName = RoleFixtures.uniqueName("ログ 鍵");
        String url = TestDatabase.url(tempDir);
        OffsetDateTime now = OffsetDateTime.parse("2026-10-08T00:00:00Z");

        int offset = output.getOut().length();
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (RowLockHolder held =
                RowLockHolder.hold(url, "SELECT role_id FROM roles WHERE role_id = ? FOR UPDATE", rowRole)) {
            assertThat(held.lockedRows()).isEqualTo(1);
            responses.add(api.save(
                    admin.token(),
                    rowRole,
                    saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null)))));
        }
        try (UncommittedWrite held = UncommittedWrite.hold(
                url,
                "INSERT INTO roles (name, name_key, created_at, updated_at) VALUES (?, ?, ?, ?)",
                keyName,
                RoleFixtures.name(keyName).key(),
                now,
                now)) {
            responses.add(api.create(admin.token(), keyName));
        }
        List<String> expectedKinds = List.of("ROLE_ROW", "ROLE_NAME_KEY");

        String logs = output.getOut().substring(offset) + output.getErr();
        List<Map<String, Object>> records = JsonLogRecords.parse(logs);
        for (int i = 0; i < responses.size(); i++) {
            HttpResponse<String> response = responses.get(i);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
            Map<String, Object> body = HttpTestClient.json(response);
            assertThat(body).containsEntry("code", "ROLE_BUSY");
            String traceId = String.valueOf(body.get("traceId"));
            List<Map<String, Object>> warnings = records.stream()
                    .filter(record -> LOCK_WARN_MESSAGE.equals(record.get("message")))
                    .filter(record -> traceId.equals(record.get("traceId")))
                    .toList();
            assertThat(warnings).as("応答の traceId の排他の WARN がちょうど1行").hasSize(1);
            Map<String, Object> warning = warnings.getFirst();
            assertThat(warning)
                    .containsEntry("level", "WARN")
                    .containsEntry("lockKind", expectedKinds.get(i))
                    .containsKey("exceptionClass");
            assertThat(ALLOWED_KEYS).as("WARN の項目").containsAll(warning.keySet());
        }
        assertThat(logs)
                .doesNotContain(rowName)
                .doesNotContain(RoleFixtures.name(keyName).key())
                .doesNotContain("Timeout trying to lock")
                .doesNotContain("Unique index or primary key violation");
    }

    @Test
    @DisplayName("a check refused at the transfer slot has one warning with its trace id and only the lock kind (R-12)")
    void slotWarning(CapturedOutput output) {
        int offset = output.getOut().length();
        HttpResponse<String> response;
        assertThat(slot.tryAcquire()).isTrue();
        try {
            response = api.check(admin.token(), "version: 1\nroles:\n  LEAKCHECK_ROLE_7f3a: {}\n");
        } finally {
            slot.release();
        }

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("code", "ROLE_BUSY");
        String logs = output.getOut().substring(offset) + output.getErr();
        List<Map<String, Object>> warnings = JsonLogRecords.parse(logs).stream()
                .filter(record -> "権限の受け渡しの入口を取れませんでした".equals(record.get("message")))
                .filter(record -> String.valueOf(body.get("traceId")).equals(record.get("traceId")))
                .toList();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst())
                .containsEntry("level", "WARN")
                .containsEntry("lockKind", "ROLE_TRANSFER_SLOT")
                .doesNotContainKey("exceptionClass");
        assertThat(ALLOWED_KEYS).containsAll(warnings.getFirst().keySet());
        assertThat(logs).doesNotContain("LEAKCHECK_ROLE_7f3a");
    }

    @Test
    @DisplayName(
            "a role assignment that waits too long for the group row is GROUP_BUSY with exactly one GROUP_ROW warning"
                    + " carrying its trace id (FR10)")
    void groupBusyOnAssignment(CapturedOutput output) throws Exception {
        long roleId = fixtures.role(RoleFixtures.uniqueName("ログ 割り当て"));
        String groupName = GroupFixtures.uniqueName("ログ グループ");
        long groupId = new GroupFixtures(users, jdbc).group(groupName);
        String url = TestDatabase.url(tempDir);

        int offset = output.getOut().length();
        HttpResponse<String> response;
        try (RowLockHolder held =
                RowLockHolder.hold(url, "SELECT group_id FROM groups WHERE group_id = ? FOR UPDATE", groupId)) {
            assertThat(held.lockedRows()).isEqualTo(1);
            response = api.assignGroup(admin.token(), roleId, groupId);
        }

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("code", "GROUP_BUSY");
        String traceId = String.valueOf(body.get("traceId"));
        String logs = output.getOut().substring(offset) + output.getErr();
        List<Map<String, Object>> warnings = JsonLogRecords.parse(logs).stream()
                .filter(record -> LOCK_WARN_MESSAGE.equals(record.get("message")))
                .filter(record -> traceId.equals(record.get("traceId")))
                .toList();
        assertThat(warnings).as("応答の traceId の排他の WARN がちょうど1行").hasSize(1);
        assertThat(warnings.getFirst())
                .containsEntry("level", "WARN")
                .containsEntry("lockKind", "GROUP_ROW")
                .containsKey("exceptionClass");
        assertThat(ALLOWED_KEYS).as("WARN の項目").containsAll(warnings.getFirst().keySet());
        assertThat(logs).doesNotContain(groupName).doesNotContain("Timeout trying to lock");
        assertThat(fixtures.assignmentRows(roleId)).isZero();
    }
}
