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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.Mode;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupActors;
import cherry.mastersmith.group.testsupport.GroupActors.Actor;
import cherry.mastersmith.group.testsupport.GroupApi;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.TestGroupBarrier;
import cherry.mastersmith.group.testsupport.TestGroupDeletionGuard;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * グループの操作の監査の書き込みが失敗しても、操作の応答と状態が変わらないことの結合テスト（BR8.1、NFR3.5、{@code reliability-design.md}
 * 3節。既存の {@code UserAdminAuditWriteFailureIT} と同じ形）。アプリのログに ERROR が1件出て、対象のグループと detail を載せ、メンバーの
 * メールアドレス・氏名を含まない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({
    AuthApiTestConfig.class,
    FailingAuditEventRepositoryConfig.class,
    TestGroupBarrier.Config.class,
    TestGroupDeletionGuard.Config.class
})
class GroupAuditWriteFailureIT {

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
    FailingAuditEventRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private GroupApi api;

    private GroupActors actors;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        repository.mode(Mode.NONE);
        admin = actors.admin();
        repository.takeSaveCalls();
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs == null
                ? Map.of()
                : pairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"APPEND_FAILURE", "CONNECTION_FAILURE"})
    @DisplayName("a failing audit write keeps a member addition at 204 with the member added and one ERROR")
    void successIsKept(Mode mode) {
        long groupId = ((Number) HttpTestClient.json(api.create(admin.token(), GroupFixtures.uniqueName("書き込み失敗")))
                        .get("groupId"))
                .longValue();
        Actor user = actors.user("見本 書き込み 失敗");
        repository.takeSaveCalls();
        repository.mode(mode);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            HttpResponse<String> response = api.addMember(admin.token(), groupId, user.userId());

            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?",
                            Integer.class,
                            groupId,
                            user.userId()))
                    .isEqualTo(1);
            assertThat(logs.list()).singleElement().satisfies(error -> {
                assertThat(error.getLevel()).isEqualTo(Level.ERROR);
                Map<String, String> fields = keyValues(error);
                assertThat(fields)
                        .containsEntry("auditEventType", "GROUP_MEMBER_ADDED")
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("actorUserId", String.valueOf(admin.userId()))
                        .containsEntry("targetUserId", String.valueOf(user.userId()))
                        .containsEntry("targetGroupId", String.valueOf(groupId))
                        .containsKey("detail");
                assertThat(error.getFormattedMessage() + " " + fields)
                        .doesNotContain(user.email())
                        .doesNotContain("見本 書き込み 失敗");
            });
        }
        assertThat(repository.takeSaveCalls()).as("再試行はしない").isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"APPEND_FAILURE", "CONNECTION_FAILURE"})
    @DisplayName("a failing audit write keeps a duplicate rejection at 409 with nothing changed")
    void rejectionIsKept(Mode mode) {
        String name = GroupFixtures.uniqueName("書き込み失敗 拒否");
        api.create(admin.token(), name);
        repository.mode(mode);

        try (LogEvents logs = LogEvents.capture(AuditEventListener.class)) {
            HttpResponse<String> response = api.create(admin.token(), name);

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(HttpTestClient.json(response)).containsEntry("code", "GROUP_NAME_DUPLICATE");
            assertThat(logs.list())
                    .singleElement()
                    .satisfies(error -> assertThat(keyValues(error))
                            .containsEntry("auditEventType", "GROUP_CREATED")
                            .containsEntry("result", "FAILURE")
                            .containsEntry("failureReason", "GROUP_NAME_DUPLICATE")
                            .doesNotContainKey("targetGroupId"));
        }
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM groups WHERE name_key = ?",
                        Integer.class,
                        name.toLowerCase(java.util.Locale.ROOT)))
                .isEqualTo(1);
    }
}
