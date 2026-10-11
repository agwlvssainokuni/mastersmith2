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
import cherry.mastersmith.navigation.testsupport.CountingResolver;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.role.service.EffectivePermissionResolver;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 想定外の失敗の結合テスト（BR7.2、NFR1.11、{@code reliability-design.md} の 3節）。解決の口を例外を投げる替え物（{@code @Primary}）に
 * し、2本の API の応答が 500 の共通の本文で、例外の文を含まないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, NavigationFailureIT.FailingResolverConfig.class})
class NavigationFailureIT {

    /** 例外の文の目印（応答に出てはならない）。 */
    static final String MESSAGE = "zz-internal-failure-0c5d";

    /** 例外を投げる解決の口。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingResolverConfig {

        @Bean
        @Primary
        EffectivePermissionResolver failingResolver() {
            return new CountingResolver(() -> null).failWith(new IllegalStateException(MESSAGE));
        }
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

    private NavigationActors actors;

    @BeforeEach
    void setUp() {
        actors = new NavigationActors(
                new RoleActors(userAccountService, revocationService, transactionManager, port),
                new RoleFixtures(jdbc),
                port);
        NavigationDslFixture.install(
                holder,
                List.of(NavigationModels.schema("SALES", "T0")),
                List.of(NavigationModels.tableItem("T0", "SALES", "T0")));
    }

    @Test
    @DisplayName("a failure of the resolver gives the common 500 body without the exception message on both APIs")
    void unexpectedFailure() {
        Actor member = actors.member();

        for (HttpResponse<String> response :
                List.of(actors.navigation(member.token()), actors.tableAccess(member.token(), "SALES", "T0"))) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(500);
            assertThat(HttpTestClient.json(response)).containsEntry("code", "INTERNAL_ERROR");
            assertThat(response.body()).doesNotContain(MESSAGE, "IllegalStateException", "items");
        }
    }
}
