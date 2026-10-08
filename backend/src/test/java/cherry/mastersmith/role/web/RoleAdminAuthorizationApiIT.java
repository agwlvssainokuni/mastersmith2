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
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * ロールの管理と権限の設定の 10 の口の認可の表（10 の口 × 4つの主体 = 40 行。BR2.1、NFR1.1〜NFR1.3、AC1.1.5・AC1.2.11、
 * {@code security-design.md} 2節）の結合テスト。{@code team.md} の必須のテスト（権限ごとの API の認可）。
 *
 * <p>主体は未認証・管理者の印を持たない利用者・管理者・停止中の管理者。B4 では作業ロールをまだ持てないため、管理者の印を持たない利用者で
 * 403 を確かめる（B5 で「要る権限だけを欠く利用者」に置き換える。計画の D-17）。停止中の管理者は既存のアクセストークンの認証の入口で
 * 拒否されるため 401（計画の D-34）。拒否の後はロールと設定の行を読み直し、変わっていないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleAdminAuthorizationApiIT {

    /** 10 の口と、管理者のときの成功の状態コード。 */
    enum Endpoint {
        LIST(200),
        CREATE(201),
        DETAIL(200),
        RENAME(204),
        DELETE(204),
        SCHEMAS(200),
        TABLES(200),
        COLUMNS(200),
        SAVE(204),
        CLEAR(204);

        private final int success;

        Endpoint(int success) {
            this.success = success;
        }
    }

    /** 主体。 */
    enum Subject {
        ANONYMOUS,
        MEMBER,
        ADMIN,
        SUSPENDED_ADMIN
    }

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

    private RoleApi api;

    private RoleActors actors;

    private RoleFixtures fixtures;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        actors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        fixtures = new RoleFixtures(jdbc);
        RoleDslFixture.install(holder, RoleDslFixture.sample());
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

    private String tokenOf(Subject subject) {
        return switch (subject) {
            case ANONYMOUS -> null;
            case MEMBER -> actors.member().token();
            case ADMIN -> actors.admin().token();
            case SUSPENDED_ADMIN -> actors.suspendedAdmin().token();
        };
    }

    private Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("roles", jdbc.queryForList("SELECT role_id, name, name_key, updated_at FROM roles ORDER BY role_id"));
        state.put(
                "settings",
                jdbc.queryForList("SELECT * FROM permission_settings ORDER BY role_id, schema_name, table_name,"
                        + " column_name"));
        return state;
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("table")
    void authorization(Endpoint endpoint, Subject subject) {
        long roleId = fixtures.role(RoleFixtures.uniqueName("認可"));
        fixtures.setting(roleId, PermissionTarget.table("SALES", "GONE"), MainPermission.READ);
        long emptyRole = fixtures.role(RoleFixtures.uniqueName("認可 空"));
        String token = tokenOf(subject);
        Map<String, Object> before = state();

        HttpResponse<String> response =
                switch (endpoint) {
                    case LIST -> api.list(token, "");
                    case CREATE -> api.create(token, RoleFixtures.uniqueName("認可 作成"));
                    case DETAIL -> api.detail(token, roleId);
                    case RENAME -> api.rename(token, roleId, RoleFixtures.uniqueName("認可 改名"));
                    case DELETE -> api.delete(token, emptyRole);
                    case SCHEMAS -> api.schemas(token, roleId);
                    case TABLES -> api.tables(token, roleId, "SALES");
                    case COLUMNS -> api.columns(token, roleId, "SALES", "ORDER_LINE");
                    case SAVE ->
                        api.save(
                                token,
                                roleId,
                                saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null))));
                    case CLEAR ->
                        api.clear(
                                token,
                                roleId,
                                RoleApi.json(Map.of(
                                        "targets", List.of(Map.of("schemaName", "SALES", "tableName", "GONE")))));
                };

        switch (subject) {
            case ADMIN -> assertThat(response.statusCode()).as(response.body()).isEqualTo(endpoint.success);
            case ANONYMOUS, SUSPENDED_ADMIN -> {
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
                assertThat(state()).as("拒否の後に状態は変わらない").isEqualTo(before);
            }
            case MEMBER -> {
                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(HttpTestClient.json(response)).containsEntry("code", "ACCESS_DENIED");
                assertThat(state()).as("拒否の後に状態は変わらない").isEqualTo(before);
            }
        }
    }
}
