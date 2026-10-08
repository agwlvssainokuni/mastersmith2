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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 自分の API の認可の表（作業ロールの2つの口と自分の権限の木の3つの口 × 3つの主体 = 15 行。BR2.2、NFR1.4、AC4.1.12・AC4.1.19・
 * AC4.2.3・AC4.2.5、計画の 8.3）の結合テスト。主体は未認証（401）・停止中の利用者（既存のアクセストークンの認証の入口で 401）・ログインした
 * 利用者（200・204）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleMeAuthorizationApiIT {

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

    /** 自分の口と、ログインした利用者のときの成功の状態コード。 */
    enum Endpoint {
        WORK_ROLE_READ(200),
        WORK_ROLE_SWITCH(204),
        MY_SCHEMAS(200),
        MY_TABLES(200),
        MY_COLUMNS(200);

        private final int success;

        Endpoint(int success) {
            this.success = success;
        }
    }

    /** 主体。 */
    enum Subject {
        ANONYMOUS,
        SUSPENDED,
        SIGNED_IN
    }

    static Stream<Arguments> table() {
        List<Arguments> rows = new ArrayList<>();
        for (Endpoint endpoint : Endpoint.values()) {
            for (Subject subject : Subject.values()) {
                rows.add(Arguments.of(endpoint, subject));
            }
        }
        return rows.stream();
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("table")
    void authorization(Endpoint endpoint, Subject subject) {
        long roleId = fixtures.role(RoleFixtures.uniqueName("自分の認可"));
        Actor member = actors.member();
        fixtures.assignUser(roleId, member.userId());
        if (subject == Subject.SUSPENDED) {
            actors.suspend(member.userId());
        }
        String token = subject == Subject.ANONYMOUS ? null : member.token();

        HttpResponse<String> response =
                switch (endpoint) {
                    case WORK_ROLE_READ -> api.workRole(token);
                    case WORK_ROLE_SWITCH -> api.switchWorkRole(token, roleId);
                    case MY_SCHEMAS -> api.mySchemas(token);
                    case MY_TABLES -> api.myTables(token, "SALES");
                    case MY_COLUMNS -> api.myColumns(token, "SALES", "ORDER_LINE");
                };

        if (subject == Subject.SIGNED_IN) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(endpoint.success);
            return;
        }
        assertCode(response, 401, "AUTHENTICATION_REQUIRED");
        assertThat(fixtures.storedWorkRole(member.userId())).as("拒否の後に保存は変わらない").isNull();
    }
}
