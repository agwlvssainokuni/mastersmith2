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
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * ID の差し替え（IDOR）の拒否（BR2.3・BR3.1、NFR1.4、AC2.1.11、{@code security-design.md} 3節）の結合テスト。存在しない・0・負の
 * グループの ID と利用者 ID、招待中の人（利用者の行が無い）で拒否され、読み直したグループとメンバーが変わらないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupAdminIdorApiIT {

    private static final List<String> MISSING_GROUPS = List.of(String.valueOf(Long.MAX_VALUE), "0", "-5");

    private static final List<String> MALFORMED_IDS = List.of("abc", "99999999999999999999", "1.5");

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

    private long groupId;

    private Actor member;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        admin = actors.admin();
        HttpResponse<String> created = api.create(admin.token(), GroupFixtures.uniqueName("IDOR"));
        groupId = ((Number) HttpTestClient.json(created).get("groupId")).longValue();
        member = actors.user("見本 所属");
        assertThat(api.addMember(admin.token(), groupId, member.userId()).statusCode())
                .isEqualTo(204);
    }

    private Map<String, Object> state() {
        return Map.of(
                "groups", jdbc.queryForList("SELECT group_id, name, updated_at FROM groups ORDER BY group_id"),
                "members", jdbc.queryForList("SELECT group_id, user_id FROM group_members ORDER BY group_id, user_id"));
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.uri().getPath()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).as(response.uri().getPath()).containsEntry("code", code);
    }

    /** 招待中の人（招待の行だけがあり、利用者の行が無い）を作り、招待先のメールアドレスを返す。 */
    private String invitation() {
        byte[] tokenHash = new byte[32];
        new SecureRandom().nextBytes(tokenHash);
        OffsetDateTime now = OffsetDateTime.parse("2026-10-08T00:00:00Z");
        String email = "invited-" + UUID.randomUUID() + "@example.com";
        jdbc.update(
                "INSERT INTO invitations (email, language, token_hash, invited_by_user_id, invited_at, expires_at,"
                        + " send_result, state) VALUES (?, 'ja', ?, ?, ?, ?, 'SENT', 'PENDING')",
                email,
                tokenHash,
                admin.userId(),
                now,
                now.plusDays(7));
        return email;
    }

    @Test
    @DisplayName(
            "missing, zero and negative group ids are 404 GROUP_NOT_FOUND on every group endpoint and change nothing")
    void missingGroups() {
        Map<String, Object> before = state();

        for (String id : MISSING_GROUPS) {
            assertCode(api.detail(admin.token(), id), 404, "GROUP_NOT_FOUND");
            assertCode(api.rename(admin.token(), id, GroupFixtures.uniqueName("IDOR 改名")), 404, "GROUP_NOT_FOUND");
            assertCode(api.delete(admin.token(), id), 404, "GROUP_NOT_FOUND");
            assertCode(api.addMember(admin.token(), id, member.userId()), 404, "GROUP_NOT_FOUND");
            assertCode(api.removeMember(admin.token(), id, member.userId()), 404, "GROUP_NOT_FOUND");
        }

        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("group ids that are not numbers are 400 on every group endpoint")
    void malformedGroupIds() {
        for (String id : MALFORMED_IDS) {
            assertThat(api.detail(admin.token(), id).statusCode()).isEqualTo(400);
            assertThat(api.rename(admin.token(), id, "IDOR").statusCode()).isEqualTo(400);
            assertThat(api.delete(admin.token(), id).statusCode()).isEqualTo(400);
            assertThat(api.addMember(admin.token(), id, member.userId()).statusCode())
                    .isEqualTo(400);
            assertThat(api.removeMember(admin.token(), id, member.userId()).statusCode())
                    .isEqualTo(400);
            assertThat(api.removeMember(admin.token(), groupId, id).statusCode())
                    .isEqualTo(400);
        }
    }

    @Test
    @DisplayName("missing, zero, negative and unused user ids cannot be added, and an invitee has no user row to add")
    void missingUsers() {
        String invited = invitation();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, invited))
                .as("招待中の人は利用者の行も利用者 ID も持たないため、足せる ID が無い")
                .isZero();
        long unused = jdbc.queryForObject("SELECT MAX(user_id) FROM users", Long.class) + 1000;
        Map<String, Object> before = state();

        for (long userId : new long[] {Long.MAX_VALUE, 0L, -5L, unused}) {
            HttpResponse<String> response = api.addMember(admin.token(), groupId, userId);
            assertCode(response, 404, "USER_NOT_FOUND");
            assertThat(response.body()).doesNotContain("invited-");
        }

        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("removing a user that is not a member (or does not exist) is no change and keeps the members")
    void removingOthers() {
        Actor other = actors.user("見本 対象外");
        Map<String, Object> before = state();

        assertCode(api.removeMember(admin.token(), groupId, other.userId()), 409, "GROUP_NO_CHANGE");
        assertCode(api.removeMember(admin.token(), groupId, Long.MAX_VALUE), 409, "GROUP_NO_CHANGE");
        assertCode(api.removeMember(admin.token(), groupId, -1), 409, "GROUP_NO_CHANGE");

        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("a member of one group cannot be removed through another group id")
    void anotherGroupsMember() {
        HttpResponse<String> created = api.create(admin.token(), GroupFixtures.uniqueName("IDOR ほか"));
        long otherGroup = ((Number) HttpTestClient.json(created).get("groupId")).longValue();
        Map<String, Object> before = state();

        assertCode(api.removeMember(admin.token(), otherGroup, member.userId()), 409, "GROUP_NO_CHANGE");

        assertThat(state()).isEqualTo(before);
    }

    @Test
    @DisplayName("a rejected change for a missing group is audited with the requested id and no detail")
    void missingGroupIsAudited() {
        long before = jdbc.queryForObject("SELECT COALESCE(MAX(audit_event_id), 0) FROM audit_events", Long.class);

        api.delete(admin.token(), Long.MAX_VALUE);
        api.detail(admin.token(), Long.MAX_VALUE);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_type, result, failure_reason, target_group_id, detail FROM audit_events"
                        + " WHERE audit_event_id > ?",
                before);
        assertThat(rows).as("読み取りの 404 は残さない").hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("EVENT_TYPE", "GROUP_DELETED")
                .containsEntry("RESULT", "FAILURE")
                .containsEntry("FAILURE_REASON", "GROUP_NOT_FOUND")
                .containsEntry("TARGET_GROUP_ID", Long.MAX_VALUE)
                .containsEntry("DETAIL", null);
    }
}
