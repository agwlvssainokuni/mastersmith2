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

import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.audit.testsupport.AuditRows.AuditRow;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.RowLockHolder;
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
import org.junit.jupiter.api.AfterEach;
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
 * グループの操作の監査（BR8.1〜BR8.5、契約 C10、NFR5.1、AC2.1.1・AC2.1.4・AC2.1.13）の結合テスト。操作ごと・拒否ごとに、種類・結果・
 * 理由・操作した人・対象のグループと利用者・detail が埋まり、入力の誤り（400）・{@code GROUP_BUSY}・読み取りは残らないことを、
 * 監査の表を読んで確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupAuditIT {

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
    TestGroupDeletionGuard guard;

    @Autowired
    JdbcTemplate jdbc;

    private GroupApi api;

    private GroupActors actors;

    private AuditRows rows;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        rows = new AuditRows(jdbc);
        admin = actors.admin();
    }

    @AfterEach
    void tearDown() {
        guard.reset();
    }

    private long lastId() {
        return rows.count() == 0 ? 0 : rows.last().auditEventId();
    }

    private List<AuditRow> since(long id) {
        return rows.all().stream().filter(row -> row.auditEventId() > id).toList();
    }

    private AuditRow onlyRowSince(long id) {
        List<AuditRow> added = since(id);
        assertThat(added).hasSize(1);
        return added.getFirst();
    }

    @Test
    @DisplayName("every successful operation leaves one SUCCESS row with the actor, the targets and the detail")
    void successes() {
        String name = GroupFixtures.uniqueName("監査");
        Actor user = actors.user("見本 監査");

        long before = lastId();
        HttpResponse<String> created = api.create(admin.token(), name);
        long groupId = ((Number) HttpTestClient.json(created).get("groupId")).longValue();
        AuditRow create = onlyRowSince(before);
        assertThat(create.eventType()).isEqualTo("GROUP_CREATED");
        assertThat(create.result()).isEqualTo("SUCCESS");
        assertThat(create.failureReason()).isNull();
        assertThat(create.actorUserId()).isEqualTo(admin.userId());
        assertThat(create.targetGroupId()).isEqualTo(groupId);
        assertThat(create.targetUserId()).isNull();
        assertThat(create.targetRoleId()).isNull();
        assertThat(create.detail()).isEqualTo("{\"name\":\"" + name + "\"}");
        assertThat(create.sourceIp()).isNotBlank();
        assertThat(create.traceId()).isNotBlank();
        assertThat(create.enteredEmail()).isNull();

        String renamed = GroupFixtures.uniqueName("監査 改名");
        before = lastId();
        api.rename(admin.token(), groupId, renamed);
        AuditRow rename = onlyRowSince(before);
        assertThat(rename.eventType()).isEqualTo("GROUP_RENAMED");
        assertThat(rename.detail()).isEqualTo("{\"before\":\"" + name + "\",\"after\":\"" + renamed + "\"}");

        before = lastId();
        api.addMember(admin.token(), groupId, user.userId());
        AuditRow add = onlyRowSince(before);
        assertThat(add.eventType()).isEqualTo("GROUP_MEMBER_ADDED");
        assertThat(add.targetUserId()).isEqualTo(user.userId());
        assertThat(add.targetGroupId()).isEqualTo(groupId);
        assertThat(add.detail()).isEqualTo("{\"groupName\":\"" + renamed + "\"}");

        before = lastId();
        api.removeMember(admin.token(), groupId, user.userId());
        assertThat(onlyRowSince(before).eventType()).isEqualTo("GROUP_MEMBER_REMOVED");

        before = lastId();
        api.delete(admin.token(), groupId);
        AuditRow delete = onlyRowSince(before);
        assertThat(delete.eventType()).isEqualTo("GROUP_DELETED");
        assertThat(delete.targetGroupId()).as("削除の後も行は残る").isEqualTo(groupId);
        assertThat(delete.detail()).isEqualTo("{\"name\":\"" + renamed + "\"}");
    }

    @Test
    @DisplayName("every business rejection leaves one FAILURE row with its reason and detail")
    void rejections() {
        String name = GroupFixtures.uniqueName("拒否");
        long groupId =
                ((Number) HttpTestClient.json(api.create(admin.token(), name)).get("groupId")).longValue();
        Actor user = actors.user("見本 拒否");
        api.addMember(admin.token(), groupId, user.userId());

        record Case(Runnable call, String type, String reason, Long group, Long target, String detail) {}
        List<Case> cases = List.of(
                new Case(
                        () -> api.create(admin.token(), name),
                        "GROUP_CREATED",
                        "GROUP_NAME_DUPLICATE",
                        null,
                        null,
                        "{\"name\":\"" + name + "\"}"),
                new Case(
                        () -> api.rename(admin.token(), groupId, name),
                        "GROUP_RENAMED",
                        "NO_CHANGE",
                        groupId,
                        null,
                        "{\"name\":\"" + name + "\"}"),
                new Case(
                        () -> api.delete(admin.token(), groupId),
                        "GROUP_DELETED",
                        "GROUP_IN_USE",
                        groupId,
                        null,
                        "{\"name\":\"" + name + "\",\"members\":1,\"assignedRoles\":0}"),
                new Case(
                        () -> api.addMember(admin.token(), groupId, user.userId()),
                        "GROUP_MEMBER_ADDED",
                        "NO_CHANGE",
                        groupId,
                        user.userId(),
                        "{\"groupName\":\"" + name + "\"}"),
                new Case(
                        () -> api.addMember(admin.token(), groupId, Long.MAX_VALUE),
                        "GROUP_MEMBER_ADDED",
                        "USER_NOT_FOUND",
                        groupId,
                        Long.MAX_VALUE,
                        null),
                new Case(
                        () -> api.removeMember(admin.token(), Long.MAX_VALUE, user.userId()),
                        "GROUP_MEMBER_REMOVED",
                        "GROUP_NOT_FOUND",
                        Long.MAX_VALUE,
                        user.userId(),
                        null));

        for (Case c : cases) {
            long before = lastId();
            c.call().run();
            AuditRow row = onlyRowSince(before);
            assertThat(row.eventType()).as(c.reason()).isEqualTo(c.type());
            assertThat(row.result()).isEqualTo("FAILURE");
            assertThat(row.failureReason()).isEqualTo(c.reason());
            assertThat(row.actorUserId()).isEqualTo(admin.userId());
            assertThat(row.targetGroupId()).isEqualTo(c.group());
            assertThat(row.targetUserId()).isEqualTo(c.target());
            assertThat(row.detail()).isEqualTo(c.detail());
        }
    }

    @Test
    @DisplayName("input errors, GROUP_BUSY and reads leave no audit row")
    void notAudited() throws Exception {
        long groupId = ((Number) HttpTestClient.json(api.create(admin.token(), GroupFixtures.uniqueName("記録なし")))
                        .get("groupId"))
                .longValue();
        long before = lastId();

        api.create(admin.token(), " ");
        api.rename(admin.token(), groupId, "a".repeat(65));
        api.addMemberJson(admin.token(), groupId, "{}");
        api.list(admin.token(), "");
        api.list(admin.token(), "?page=0");
        api.detail(admin.token(), groupId);
        api.detail(admin.token(), Long.MAX_VALUE);
        try (RowLockHolder holder = RowLockHolder.hold(
                TestDatabase.url(tempDir), "SELECT group_id FROM groups WHERE group_id = ? FOR UPDATE", groupId)) {
            assertThat(holder.lockedRows()).isEqualTo(1);
            assertThat(HttpTestClient.json(api.delete(admin.token(), groupId))).containsEntry("code", "GROUP_BUSY");
        }

        assertThat(since(before)).isEmpty();
    }
}
