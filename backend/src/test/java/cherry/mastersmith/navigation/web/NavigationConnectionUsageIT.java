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
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.ConnectionCountingConfig;
import cherry.mastersmith.navigation.testsupport.ConnectionCountingDataSource;
import cherry.mastersmith.navigation.testsupport.MeasureIdFilter;
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
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
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
 * 1要求で同時に持つ接続の数の結合テスト（NFR2.6、BR6.2、{@code scalability-design.md} の 2節、計画の 2.2 の R-09 の手当て）。
 *
 * <p>ログイン（トークンの取得）をすべて終えた後、測る要求の直前に {@link ConnectionCountingDataSource#reset()} を呼び、要求の頭
 * {@value MeasureIdFilter#HEADER} を付けて1つずつ送る。印ごとの同時の接続の最大が 1 であることを確かめ、0 ならテストの用意の誤りとして
 * 落とす。印の要求の始まりで、そのスレッドの借りている数が 0 であることも確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, ConnectionCountingConfig.class})
class NavigationConnectionUsageIT {

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

    @Autowired
    DataSource dataSource;

    private NavigationActors actors;

    @BeforeEach
    void setUp() {
        actors = new NavigationActors(
                new RoleActors(userAccountService, revocationService, transactionManager, port),
                new RoleFixtures(jdbc),
                port);
        NavigationDslFixture.install(
                holder,
                List.of(NavigationModels.schema("SALES", "T0", "T1")),
                List.of(
                        NavigationModels.tableItem("T0", "SALES", "T0"),
                        NavigationModels.tableItem("T1", "SALES", "T1")));
    }

    /** 印を付けて1要求を送り、印ごとの接続の最大を返す。 */
    private int measure(ConnectionCountingDataSource counter, Function<String, HttpResponse<String>> send, int status) {
        String measureId = UUID.randomUUID().toString();
        HttpResponse<String> response = send.apply(measureId);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(counter.startInUse(measureId)).as("印の要求の始まりの借りている数").contains(0);
        int peak = counter.peak(measureId).orElseThrow(() -> new AssertionError("印の要求が数えられていない（テストの用意の誤り）"));
        assertThat(peak).as("最大 0 はテストの用意の誤り（解決の口が DB を読んでいない）").isNotZero();
        return peak;
    }

    @Test
    @DisplayName("the menu uses at most one connection at a time per request")
    void oneConnectionPerRequest() {
        Actor member = actors.memberWithWorkRole(
                actors.role("SALES", Map.of("T0", MainPermission.READ, "T1", MainPermission.NONE)));
        ConnectionCountingDataSource counter = ConnectionCountingConfig.counter(dataSource);
        counter.reset();

        int menu = measure(counter, id -> actors.navigation(member.token(), MeasureIdFilter.HEADER, id), 200);

        assertThat(menu).isEqualTo(1);
    }
}
