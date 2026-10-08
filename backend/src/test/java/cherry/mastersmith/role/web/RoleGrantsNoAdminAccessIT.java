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
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ロールで管理の権限が増えないことの結合テスト（AC2.2.14、要件 C1、BR2.1、計画の 13節 Q3: A・D-28、group の読み直しの R-04）。
 *
 * <p>本番の口の一覧（{@link RequestMappingHandlerMapping}）から {@code ApiAccess(ADMIN)} の口をすべて集め、道の変数に {@code 1} を入れて、
 * テストの DSL のすべてのスキーマに FULL・CREATE と DELETE を可にしたロールを作業ロールに持ち、管理者の印だけを欠く利用者で送り、どれも 403
 * {@code ACCESS_DENIED} になることを確かめる。口を手で並べないため、後で足した管理の口も自動で対象になる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleGrantsNoAdminAccessIT {

    /** 方法の宣言が無い口で確かめる方法。 */
    private static final List<String> DEFAULT_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

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
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private RoleApi api;

    private RoleActors actors;

    private RoleFixtures fixtures;

    private GroupFixtures groups;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        actors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        fixtures = new RoleFixtures(jdbc);
        groups = new GroupFixtures(users, jdbc);
        admin = actors.admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> jsonList(HttpResponse<String> response) {
        return tools.jackson.databind.json.JsonMapper.shared().readValue(response.body(), List.class);
    }

    private int auditRows(String eventType, String result) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_type = ? AND result = ?",
                Integer.class,
                eventType,
                result);
        return count == null ? 0 : count;
    }

    private static Optional<ApiAccessLevel> levelOf(HandlerMethod handler) {
        ApiAccess onMethod = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ApiAccess.class);
        if (onMethod != null) {
            return Optional.of(onMethod.value());
        }
        return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(
                        handler.getMethod().getDeclaringClass(), ApiAccess.class))
                .map(ApiAccess::value);
    }

    private static List<String> methodsOf(RequestMappingInfo info) {
        Set<RequestMethod> declared = info.getMethodsCondition().getMethods();
        return declared.isEmpty()
                ? DEFAULT_METHODS
                : declared.stream().map(RequestMethod::name).sorted().toList();
    }

    @Test
    @DisplayName("a user with a full work role but without the admin flag is refused by every admin endpoint")
    void everyAdminEndpointRefuses() {
        Actor fullRole = actors.adminFlagMissingFullRole(fixtures);
        List<String> checked = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            if (levelOf(entry.getValue()).orElse(null) != ApiAccessLevel.ADMIN) {
                continue;
            }
            for (String pattern : entry.getKey().getPatternValues()) {
                String uri = pattern.replaceAll("\\{[^}]*}", "1");
                for (String method : methodsOf(entry.getKey())) {
                    String body = List.of("POST", "PUT", "PATCH").contains(method) ? "{}" : null;
                    HttpResponse<String> response = api.send(method, uri, fullRole.token(), body);
                    assertThat(response.statusCode())
                            .as(method + " " + uri + " " + response.body())
                            .isEqualTo(403);
                    assertThat(HttpTestClient.json(response)).containsEntry("code", "ACCESS_DENIED");
                    checked.add(method + " " + uri);
                }
            }
        }

        assertThat(checked)
                .as("確かめた管理の口（ロール・グループ・利用者・招待・DSL の管理を含む）")
                .hasSizeGreaterThanOrEqualTo(30)
                .contains(
                        "GET /api/admin/roles",
                        "POST /api/admin/roles/1/assignments",
                        "GET /api/admin/groups/1/roles",
                        "GET /api/admin/users/1/roles",
                        "GET /api/admin/groups");
        assertThat(jdbc.queryForObject(
                        "SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, fullRole.userId()))
                .isFalse();
    }

    @Test
    @DisplayName("the same user still reads its own work role and permissions")
    void ownApisStillWork() {
        Actor fullRole = actors.adminFlagMissingFullRole(fixtures);

        assertThat(api.workRole(fullRole.token()).statusCode()).isEqualTo(200);
        assertThat(api.mySchemas(fullRole.token()).body()).contains("\"main\":\"FULL\"");
    }
}
