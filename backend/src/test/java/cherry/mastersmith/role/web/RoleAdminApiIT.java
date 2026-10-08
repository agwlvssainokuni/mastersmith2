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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
 * ロールの管理の5つの口（契約 C7、BR1・BR3・BR13、AC1.1.1・AC1.1.2・AC1.1.4・AC1.1.9・AC1.1.14・AC1.1.17）の結合テスト。成功の応答の
 * 形、業務の拒否の code、入力の誤りの {@code fieldErrors}、Problem Details の形を確かめる。削除は割り当てが無い前提（B4）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleAdminApiIT {

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

    private RoleApi api;

    private RoleFixtures fixtures;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
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
        return ((Number) json(response).get("roleId")).longValue();
    }

    private int roleAuditRows() {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE event_type LIKE 'ROLE_%'", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("creating answers 201 with the trimmed name and times, and the role appears in the list and detail")
    void create() {
        String name = RoleFixtures.uniqueName("営業");

        HttpResponse<String> response = api.create(admin.token(), "　" + name + " ");

        assertThat(response.statusCode()).isEqualTo(201);
        Map<String, Object> body = json(response);
        assertThat(body)
                .containsOnlyKeys("roleId", "name", "createdAt", "updatedAt")
                .containsEntry("name", name);
        assertThat(body.get("createdAt")).isEqualTo(body.get("updatedAt"));
        long roleId = ((Number) body.get("roleId")).longValue();
        HttpResponse<String> detail = api.detail(admin.token(), roleId);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json(detail)).isEqualTo(body);
        HttpResponse<String> list = api.list(admin.token(), "");
        assertThat(json(list))
                .containsOnlyKeys("items", "page", "size", "total")
                .containsEntry("size", 20);
        assertThat(list.body())
                .contains("{\"roleId\":" + roleId + ",\"name\":\"" + name + "\",\"userCount\":0,\"groupCount\":0}");
    }

    @Test
    @DisplayName("renaming answers 204, deleting removes the role with its settings, and a missing role is 404")
    void lifecycle() {
        long roleId = created(RoleFixtures.uniqueName("一連"));
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        String renamed = RoleFixtures.uniqueName("改名");

        assertThat(api.rename(admin.token(), roleId, renamed).statusCode()).isEqualTo(204);
        assertThat(json(api.detail(admin.token(), roleId))).containsEntry("name", renamed);
        assertThat(api.delete(admin.token(), roleId).statusCode()).isEqualTo(204);

        assertCode(api.detail(admin.token(), roleId), 404, "ROLE_NOT_FOUND");
        assertThat(fixtures.settingRows(roleId)).as("AC1.1.4 設定も消える").isZero();
        assertCode(api.delete(admin.token(), roleId), 404, "ROLE_NOT_FOUND");
        assertCode(api.rename(admin.token(), roleId, renamed), 404, "ROLE_NOT_FOUND");
    }

    @Test
    @DisplayName("name errors answer 400 VALIDATION_FAILED with a name field error and leave no audit row")
    void nameErrors() {
        long roleId = created(RoleFixtures.uniqueName("誤り"));
        int before = roleAuditRows();

        Map<String, String> cases =
                Map.of("", "REQUIRED", " 　 ", "REQUIRED", "a".repeat(65), "TOO_LONG", "営業\n部", "INVALID_CHARACTER");
        cases.forEach((name, reason) -> {
            for (HttpResponse<String> response :
                    List.of(api.create(admin.token(), name), api.rename(admin.token(), roleId, name))) {
                assertCode(response, 400, "VALIDATION_FAILED");
                assertThat(json(response))
                        .containsEntry("fieldErrors", List.of(Map.of("field", "name", "reason", reason)));
            }
        });
        assertCode(api.createJson(admin.token(), "{}"), 400, "VALIDATION_FAILED");
        assertThat(api.rename(admin.token(), "abc", "x").statusCode()).isEqualTo(400);
        assertThat(roleAuditRows()).as("入力の誤りは監査に残さない").isEqualTo(before);
        assertThat(api.create(admin.token(), "😀".repeat(64)).statusCode())
                .as("AC1.1.9 64 コードポイント")
                .isEqualTo(201);
    }

    @Test
    @DisplayName("names collide ignoring case, a case-only rename is accepted and the same name is no change")
    void duplicatesAndNoChange() {
        String sales = RoleFixtures.uniqueName("Sales");
        long roleId = created(sales);
        long other = created(RoleFixtures.uniqueName("Other"));

        assertCode(api.create(admin.token(), sales.toLowerCase(Locale.ROOT)), 409, "ROLE_NAME_DUPLICATE");
        assertCode(api.rename(admin.token(), other, sales.toUpperCase(Locale.ROOT)), 409, "ROLE_NAME_DUPLICATE");
        assertThat(api.rename(admin.token(), roleId, sales.toUpperCase(Locale.ROOT))
                        .statusCode())
                .isEqualTo(204);
        assertCode(api.rename(admin.token(), roleId, sales.toUpperCase(Locale.ROOT)), 409, "ROLE_NO_CHANGE");
        assertThat(api.create(admin.token(), "Ｓａｌｅｓ" + sales.substring(5)).statusCode())
                .as("全角は別の名前")
                .isEqualTo(201);
    }

    @Test
    @DisplayName("the list takes only page in the id order: invalid pages are 400 and a page past the end is empty")
    void paging() {
        for (int i = 0; i < 21; i++) {
            created(RoleFixtures.uniqueName("ページ"));
        }

        assertCode(api.list(admin.token(), "?page=0"), 400, "VALIDATION_FAILED");
        assertCode(api.list(admin.token(), "?page=abc"), 400, "VALIDATION_FAILED");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> first = (List<Map<String, Object>>)
                json(api.list(admin.token(), "?size=5")).get("items");
        assertThat(first).hasSize(20);
        assertThat(first)
                .extracting(row -> ((Number) row.get("roleId")).longValue())
                .isSorted();
        HttpResponse<String> beyond = api.list(admin.token(), "?page=9999");
        assertThat(json(beyond)).containsEntry("items", List.of()).containsEntry("page", 9999);
        assertThat(((Number) json(beyond).get("total")).intValue()).isGreaterThanOrEqualTo(21);
    }

    @Test
    @DisplayName("rejections are Problem Details in Japanese and English without names or ids in the texts")
    void problemDetails() {
        String name = RoleFixtures.uniqueName("説明");
        long roleId = created(name);

        HttpResponse<String> ja = api.create(admin.token(), name);
        HttpResponse<String> en = api.create(admin.token(), name, "Accept-Language", "en-US,en;q=0.9");
        HttpResponse<String> notFound = api.detail(admin.token(), Long.MAX_VALUE);

        assertThat(ja.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(json(ja))
                .containsEntry("status", 409)
                .containsEntry("code", "ROLE_NAME_DUPLICATE")
                .containsEntry("title", "同じ名前のロールがあります")
                .containsKey("type");
        assertThat(json(en)).containsEntry("title", "A role with the same name exists");
        assertThat(json(notFound)).containsEntry("title", "ロールが見つかりません");
        for (HttpResponse<String> response : List.of(ja, en, notFound)) {
            for (String key : new String[] {"title", "detail"}) {
                assertThat(String.valueOf(json(response).get(key)))
                        .doesNotContain(name)
                        .doesNotContain(String.valueOf(roleId))
                        .doesNotContain(String.valueOf(Long.MAX_VALUE));
            }
            assertThat(response.body()).doesNotContain("Exception").doesNotContain("PUBLIC.");
        }
    }
}
