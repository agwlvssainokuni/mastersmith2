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
import java.util.LinkedHashMap;
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

/**
 * 一括代入の防止（BR2.2、NFR1.4、{@code security-design.md} 3節）の結合テスト。要求の本文に許していない項目（ID・時刻・作成者）を足しても
 * 反映されず、決めた項目だけで処理されることを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupAdminMassAssignmentIT {

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

    private Map<String, Object> row(long groupId) {
        return jdbc.queryForMap("SELECT group_id, name, created_at FROM groups WHERE group_id = ?", groupId);
    }

    @Test
    @DisplayName("extra fields in a creation are ignored: the id and the creation time come from the server")
    void createIgnoresExtraFields() {
        String name = GroupFixtures.uniqueName("一括代入 作成");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("groupId", 999_999);
        body.put("createdAt", "2000-01-01T00:00:00Z");
        body.put("nameKey", "forged");
        body.put("createdBy", admin.userId() + 1);

        HttpResponse<String> response = api.createJson(admin.token(), GroupApi.json(body));

        assertThat(response.statusCode()).isEqualTo(201);
        long groupId = ((Number) HttpTestClient.json(response).get("groupId")).longValue();
        assertThat(groupId).isNotEqualTo(999_999L);
        assertThat(String.valueOf(HttpTestClient.json(response).get("createdAt")))
                .doesNotStartWith("2000");
        assertThat(jdbc.queryForObject("SELECT name_key FROM groups WHERE group_id = ?", String.class, groupId))
                .isEqualTo(name.toLowerCase(java.util.Locale.ROOT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM groups WHERE group_id = 999999", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("extra fields in a rename are ignored: only the name changes")
    void renameIgnoresExtraFields() {
        HttpResponse<String> created = api.create(admin.token(), GroupFixtures.uniqueName("一括代入 改名"));
        long groupId = ((Number) HttpTestClient.json(created).get("groupId")).longValue();
        Map<String, Object> before = row(groupId);
        String renamed = GroupFixtures.uniqueName("一括代入 新名");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", renamed);
        body.put("groupId", groupId + 1000);
        body.put("createdAt", "2000-01-01T00:00:00Z");

        assertThat(api.renameJson(admin.token(), groupId, GroupApi.json(body)).statusCode())
                .isEqualTo(204);

        Map<String, Object> after = row(groupId);
        assertThat(after.get("NAME")).isEqualTo(renamed);
        assertThat(after.get("GROUP_ID")).isEqualTo(before.get("GROUP_ID"));
        assertThat(after.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
    }

    @Test
    @DisplayName("extra fields in a member addition are ignored: only the given user is added to the group in the path")
    void addMemberIgnoresExtraFields() {
        HttpResponse<String> created = api.create(admin.token(), GroupFixtures.uniqueName("一括代入 追加"));
        long groupId = ((Number) HttpTestClient.json(created).get("groupId")).longValue();
        HttpResponse<String> other = api.create(admin.token(), GroupFixtures.uniqueName("一括代入 ほか"));
        long otherGroup = ((Number) HttpTestClient.json(other).get("groupId")).longValue();
        Actor user = actors.user("見本 追加");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.userId());
        body.put("groupId", otherGroup);
        body.put("addedAt", "2000-01-01T00:00:00Z");
        body.put("admin", true);

        assertThat(api.addMemberJson(admin.token(), groupId, GroupApi.json(body))
                        .statusCode())
                .isEqualTo(204);

        assertThat(jdbc.queryForList("SELECT group_id FROM group_members WHERE user_id = ?", Long.class, user.userId()))
                .isEqualTo(List.of(groupId));
        assertThat(jdbc.queryForObject("SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, user.userId()))
                .isFalse();
        assertThat(String.valueOf(jdbc.queryForObject(
                        "SELECT added_at FROM group_members WHERE user_id = ?", Object.class, user.userId())))
                .doesNotStartWith("2000");
    }
}
