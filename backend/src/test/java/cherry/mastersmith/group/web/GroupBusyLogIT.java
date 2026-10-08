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
package cherry.mastersmith.group.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupActors;
import cherry.mastersmith.group.testsupport.GroupActors.Actor;
import cherry.mastersmith.group.testsupport.GroupApi;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.TestGroupBarrier;
import cherry.mastersmith.group.testsupport.TestGroupDeletionGuard;
import cherry.mastersmith.group.testsupport.UncommittedWrite;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * {@code GROUP_BUSY} のログ（{@code observability-design.md} 3節、NFR5.3、BR5.2・BR9.3）の結合テスト。行の排他・名前の鍵・メンバーの
 * 主キーの3つの待ちの上限切れを、別の接続で行を持ち続ける形で起こし、応答の {@code traceId} ごとに、排他の種類と例外のクラスの名前
 * だけを持つ WARN がちょうど1行あること、名前・ID の値・例外の文・スタックトレースが無いことを確かめる（既存の
 * {@code UserAdminBusyLogTraceIT} と同じ形）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class GroupBusyLogIT {

    /** 排他の失敗の WARN の決まった文（{@code RowLockFailures.WARN_MESSAGE}）。 */
    private static final String LOCK_WARN_MESSAGE = "行の排他を取れませんでした";

    /** WARN に出てよい項目（ログの共通の項目と、排他の種類・例外のクラスの名前）。 */
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "timestamp", "level", "logger", "thread", "message", "traceId", "spanId", "lockKind", "exceptionClass");

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

    private GroupApi api;

    private GroupActors actors;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        admin = actors.admin();
    }

    private long group(String name) {
        HttpResponse<String> created = api.create(admin.token(), name);
        return ((Number) HttpTestClient.json(created).get("groupId")).longValue();
    }

    @Test
    @DisplayName("each GROUP_BUSY has exactly one lock warning with its trace id, lock kind and exception class only")
    void oneWarningPerBusy(CapturedOutput output) throws Exception {
        String rowName = GroupFixtures.uniqueName("ログ 行");
        long rowGroup = group(rowName);
        String keyName = GroupFixtures.uniqueName("ログ 鍵");
        long memberGroup = group(GroupFixtures.uniqueName("ログ 主キー"));
        Actor user = actors.user("見本 ログ");
        String url = TestDatabase.url(tempDir);
        OffsetDateTime now = OffsetDateTime.parse("2026-10-08T00:00:00Z");

        int offset = output.getOut().length();
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (RowLockHolder held =
                RowLockHolder.hold(url, "SELECT group_id FROM groups WHERE group_id = ? FOR UPDATE", rowGroup)) {
            assertThat(held.lockedRows()).isEqualTo(1);
            responses.add(api.delete(admin.token(), rowGroup));
        }
        try (UncommittedWrite held = UncommittedWrite.hold(
                url,
                "INSERT INTO groups (name, name_key, created_at, updated_at) VALUES (?, ?, ?, ?)",
                keyName,
                keyName.toLowerCase(Locale.ROOT),
                now,
                now)) {
            responses.add(api.create(admin.token(), keyName));
        }
        try (UncommittedWrite held = UncommittedWrite.hold(
                url,
                "INSERT INTO group_members (group_id, user_id, added_at) VALUES (?, ?, ?)",
                memberGroup,
                user.userId(),
                now)) {
            responses.add(api.addMember(admin.token(), memberGroup, user.userId()));
        }
        List<String> expectedKinds = List.of("GROUP_ROW", "GROUP_NAME_KEY", "GROUP_MEMBER_KEY");

        String logs = output.getOut().substring(offset) + output.getErr();
        List<Map<String, Object>> records = JsonLogRecords.parse(logs);
        for (int i = 0; i < responses.size(); i++) {
            HttpResponse<String> response = responses.get(i);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
            Map<String, Object> body = HttpTestClient.json(response);
            assertThat(body).containsEntry("code", "GROUP_BUSY");
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
                .doesNotContain(keyName.toLowerCase(Locale.ROOT))
                .doesNotContain("Timeout trying to lock")
                .doesNotContain("Unique index or primary key violation");
    }
}
