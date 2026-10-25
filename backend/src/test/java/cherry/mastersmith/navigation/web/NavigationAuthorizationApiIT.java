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
package cherry.mastersmith.navigation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
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
 * 2本の API の認可の表（BR6.1、NFR1.2、{@code security-design.md} の 1節、{@code team.md} の「権限ごとの API の認可」）。403 を確かめる
 * 主体は、要るテーブルだけ NONE でほかのテーブルは READ の作業ロールを持つ利用者にする。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class NavigationAuthorizationApiIT {

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

    private RoleActors roleActors;

    private NavigationActors actors;

    @BeforeEach
    void setUp() {
        roleActors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        actors = new NavigationActors(roleActors, new RoleFixtures(jdbc), port);
        NavigationDslFixture.install(
                holder,
                List.of(NavigationModels.schema("SALES", "ORDER", "LINE", "HEAD")),
                List.of(
                        NavigationModels.tableItem("受注", "SALES", "ORDER"),
                        NavigationModels.tableItem("明細", "SALES", "LINE")));
    }

    /** 主体。 */
    enum Subject {
        ANONYMOUS,
        SUSPENDED,
        READ,
        FULL,
        ONLY_THIS_NONE
    }

    /** 認可の表（メニューの API 1本。削除した置き場の API の行は FR3.3 で外した）。最後の数はメニューに残る項目の数。 */
    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(Subject.ANONYMOUS, 401, "AUTHENTICATION_REQUIRED", 0),
                Arguments.of(Subject.SUSPENDED, 401, "AUTHENTICATION_REQUIRED", 0),
                Arguments.of(Subject.READ, 200, null, 2),
                Arguments.of(Subject.FULL, 200, null, 2),
                Arguments.of(Subject.ONLY_THIS_NONE, 200, null, 1));
    }

    @ParameterizedTest(name = "menu by {0} gives {1}")
    @MethodSource("table")
    void authorization(Subject subject, int status, String code, int items) {
        MainPermission order =
                switch (subject) {
                    case FULL -> MainPermission.FULL;
                    case ONLY_THIS_NONE -> MainPermission.NONE;
                    default -> MainPermission.READ;
                };
        long roleId =
                actors.role("SALES", Map.of("ORDER", order, "LINE", MainPermission.READ, "HEAD", MainPermission.READ));
        Actor member = actors.memberWithWorkRole(roleId);
        if (subject == Subject.SUSPENDED) {
            roleActors.suspend(member.userId());
        }
        String token = subject == Subject.ANONYMOUS ? null : member.token();

        HttpResponse<String> response = actors.navigation(token);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        if (code != null) {
            assertThat(HttpTestClient.json(response)).containsEntry("code", code);
        } else {
            assertThat(HttpTestClient.json(response).get("items"))
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .hasSize(items);
        }
    }
}
