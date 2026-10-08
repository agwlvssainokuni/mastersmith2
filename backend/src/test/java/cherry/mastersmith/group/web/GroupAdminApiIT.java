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
import java.util.List;
import java.util.Map;
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
 * グループの管理の7つの口（契約 C6、BR1〜BR4・BR7・BR10、AC2.1.1〜AC2.1.4・AC2.1.7・AC2.1.8・AC2.1.13）の結合テスト。成功の応答の
 * 形、業務の拒否の code、入力の誤りの {@code fieldErrors}、Problem Details の形を確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupAdminApiIT {

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

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        admin = actors.admin();
    }

    @AfterEach
    void tearDown() {
        guard.reset();
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response)).containsEntry("code", code);
    }

    private long created(String name) {
        HttpResponse<String> response = api.create(admin.token(), name);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return ((Number) json(response).get("groupId")).longValue();
    }

    private int groupAuditRows() {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE event_type LIKE 'GROUP_%'", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("creating answers 201 with the trimmed name and times, and the group appears in the list and detail")
    void create() {
        String name = GroupFixtures.uniqueName("営業部");

        HttpResponse<String> response = api.create(admin.token(), "　" + name + " ");

        assertThat(response.statusCode()).isEqualTo(201);
        Map<String, Object> body = json(response);
        assertThat(body)
                .containsOnlyKeys("groupId", "name", "createdAt", "updatedAt")
                .containsEntry("name", name);
        assertThat(body.get("createdAt")).isEqualTo(body.get("updatedAt"));
        long groupId = ((Number) body.get("groupId")).longValue();
        HttpResponse<String> detail = api.detail(admin.token(), groupId);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json(detail))
                .containsOnlyKeys("groupId", "name", "members")
                .containsEntry("name", name)
                .containsEntry("members", List.of());
        HttpResponse<String> list = api.list(admin.token(), "");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json(list))
                .containsOnlyKeys("items", "page", "size", "total")
                .containsEntry("size", 20);
        assertThat(list.body()).contains("\"groupId\":" + groupId);
    }

    @Test
    @DisplayName(
            "members are added in order with their values, renaming and removing answer 204 and deleting an empty group")
    void lifecycle() {
        long groupId = created(GroupFixtures.uniqueName("一連"));
        Actor first = actors.user("営業 太郎");
        Actor second = actors.user("営業 花子");
        actors.user("見本 対象外");
        jdbc.update("UPDATE users SET suspended = TRUE WHERE user_id = ?", second.userId());

        assertThat(api.addMember(admin.token(), groupId, first.userId()).statusCode())
                .isEqualTo(204);
        assertThat(api.addMember(admin.token(), groupId, second.userId()).statusCode())
                .as("停止中の利用者も足せる（BR3.2）")
                .isEqualTo(204);
        HttpResponse<String> detail = api.detail(admin.token(), groupId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members =
                (List<Map<String, Object>>) json(detail).get("members");
        assertThat(members)
                .extracting(member -> ((Number) member.get("userId")).longValue())
                .containsExactly(first.userId(), second.userId());
        assertThat(members.get(0))
                .containsOnlyKeys("userId", "displayName", "email", "suspended")
                .containsEntry("displayName", "営業 太郎")
                .containsEntry("email", first.email())
                .containsEntry("suspended", false);
        assertThat(members.get(1)).containsEntry("suspended", true);

        String renamed = GroupFixtures.uniqueName("改名");
        assertThat(api.rename(admin.token(), groupId, renamed).statusCode()).isEqualTo(204);
        assertThat(json(api.detail(admin.token(), groupId))).containsEntry("name", renamed);

        HttpResponse<String> inUse = api.delete(admin.token(), groupId);
        assertCode(inUse, 409, "GROUP_IN_USE");
        assertThat(json(inUse)).containsEntry("members", 2).containsEntry("assignedRoles", 0);

        assertThat(api.removeMember(admin.token(), groupId, first.userId()).statusCode())
                .isEqualTo(204);
        assertThat(api.removeMember(admin.token(), groupId, second.userId()).statusCode())
                .isEqualTo(204);
        assertThat(api.delete(admin.token(), groupId).statusCode()).isEqualTo(204);
        assertCode(api.detail(admin.token(), groupId), 404, "GROUP_NOT_FOUND");
    }

    @Test
    @DisplayName("name errors answer 400 VALIDATION_FAILED with a name field error and leave no audit row")
    void nameErrors() {
        long groupId = created(GroupFixtures.uniqueName("誤り"));
        int before = groupAuditRows();

        Map<String, String> cases = Map.of(
                "",
                "REQUIRED",
                " 　 ",
                "REQUIRED",
                "a".repeat(65),
                "TOO_LONG",
                "営業\n部",
                "INVALID_CHARACTER",
                "営業\t部",
                "INVALID_CHARACTER");
        cases.forEach((name, reason) -> {
            for (HttpResponse<String> response :
                    List.of(api.create(admin.token(), name), api.rename(admin.token(), groupId, name))) {
                assertCode(response, 400, "VALIDATION_FAILED");
                assertThat(json(response))
                        .containsEntry("fieldErrors", List.of(Map.of("field", "name", "reason", reason)));
            }
        });
        assertCode(api.createJson(admin.token(), "{}"), 400, "VALIDATION_FAILED");
        assertThat(api.create(admin.token(), "a".repeat(64)).statusCode()).isEqualTo(201);

        assertThat(groupAuditRows()).as("入力の誤りは監査に残さない").isEqualTo(before + 1);
    }

    @Test
    @DisplayName("names collide ignoring case, a case-only rename is accepted and the same name is no change")
    void duplicatesAndNoChange() {
        String sales = GroupFixtures.uniqueName("Sales");
        long groupId = created(sales);
        long other = created(GroupFixtures.uniqueName("Other"));

        assertCode(api.create(admin.token(), sales.toLowerCase(java.util.Locale.ROOT)), 409, "GROUP_NAME_DUPLICATE");
        assertCode(
                api.rename(admin.token(), other, sales.toUpperCase(java.util.Locale.ROOT)),
                409,
                "GROUP_NAME_DUPLICATE");
        assertThat(api.rename(admin.token(), groupId, sales.toUpperCase(java.util.Locale.ROOT))
                        .statusCode())
                .isEqualTo(204);
        assertCode(
                api.rename(admin.token(), groupId, sales.toUpperCase(java.util.Locale.ROOT)), 409, "GROUP_NO_CHANGE");
        assertThat(api.create(admin.token(), "Ｓａｌｅｓ" + sales.substring(5)).statusCode())
                .as("全角は別の名前")
                .isEqualTo(201);
    }

    @Test
    @DisplayName(
            "member rules: repeated add and removing a non-member are no change, unknown or missing users are refused")
    void memberRules() {
        long groupId = created(GroupFixtures.uniqueName("メンバー"));
        Actor user = actors.user("営業 次郎");
        assertThat(api.addMember(admin.token(), groupId, user.userId()).statusCode())
                .isEqualTo(204);

        assertCode(api.addMember(admin.token(), groupId, user.userId()), 409, "GROUP_NO_CHANGE");
        assertCode(api.removeMember(admin.token(), groupId, Long.MAX_VALUE), 409, "GROUP_NO_CHANGE");
        assertCode(api.addMember(admin.token(), groupId, Long.MAX_VALUE), 404, "USER_NOT_FOUND");
        HttpResponse<String> missing = api.addMemberJson(admin.token(), groupId, "{}");
        assertCode(missing, 400, "VALIDATION_FAILED");
        assertThat(json(missing))
                .containsEntry("fieldErrors", List.of(Map.of("field", "userId", "reason", "REQUIRED")));
        assertThat(api.addMemberJson(admin.token(), groupId, "{\"userId\":\"abc\"}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(api.removeMember(admin.token(), groupId, user.userId()).statusCode())
                .isEqualTo(204);
        assertCode(api.removeMember(admin.token(), groupId, user.userId()), 409, "GROUP_NO_CHANGE");
    }

    @Test
    @DisplayName("a group with assigned roles is in use and the list carries the counts of members and roles")
    void assignedRoles() {
        long groupId = created(GroupFixtures.uniqueName("割り当て"));
        guard.assign(groupId, 2);

        HttpResponse<String> response = api.delete(admin.token(), groupId);

        assertCode(response, 409, "GROUP_IN_USE");
        assertThat(json(response)).containsEntry("members", 0).containsEntry("assignedRoles", 2);
        int total = ((Number) json(api.list(admin.token(), "")).get("total")).intValue();
        int lastPage = (total - 1) / 20 + 1;
        assertThat(api.list(admin.token(), "?page=" + lastPage).body())
                .contains("{\"groupId\":" + groupId + ",")
                .contains("\"memberCount\":0,\"assignedRoleCount\":2");
    }

    @Test
    @DisplayName("the list takes only page: invalid pages are 400 and a page past the end is empty with the total")
    void paging() {
        created(GroupFixtures.uniqueName("ページ"));

        assertCode(api.list(admin.token(), "?page=0"), 400, "VALIDATION_FAILED");
        assertCode(api.list(admin.token(), "?page=abc"), 400, "VALIDATION_FAILED");
        HttpResponse<String> beyond = api.list(admin.token(), "?page=9999&size=1");
        assertThat(beyond.statusCode()).isEqualTo(200);
        assertThat(json(beyond))
                .containsEntry("items", List.of())
                .containsEntry("page", 9999)
                .containsEntry("size", 20);
        assertThat(((Number) json(beyond).get("total")).intValue()).isPositive();
    }

    @Test
    @DisplayName("rejections are Problem Details in Japanese and English without names or ids in the texts")
    void problemDetails() {
        String name = GroupFixtures.uniqueName("説明");
        long groupId = created(name);

        HttpResponse<String> ja = api.create(admin.token(), name);
        HttpResponse<String> en =
                api.createJson(admin.token(), GroupApi.json(Map.of("name", name)), "Accept-Language", "en-US,en;q=0.9");
        HttpResponse<String> notFound = api.detail(admin.token(), Long.MAX_VALUE);

        assertThat(ja.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(json(ja))
                .containsEntry("status", 409)
                .containsEntry("code", "GROUP_NAME_DUPLICATE")
                .containsEntry("title", "同じ名前のグループがあります")
                .containsKey("type");
        assertThat(json(en)).containsEntry("title", "A group with the same name exists");
        assertThat(json(notFound)).containsEntry("title", "グループが見つかりません");
        for (HttpResponse<String> response : List.of(ja, en, notFound)) {
            for (String key : new String[] {"title", "detail"}) {
                assertThat(String.valueOf(json(response).get(key)))
                        .doesNotContain(name)
                        .doesNotContain(String.valueOf(groupId))
                        .doesNotContain(String.valueOf(Long.MAX_VALUE));
            }
            assertThat(response.body()).doesNotContain("Exception").doesNotContain("PUBLIC.");
        }
    }
}
