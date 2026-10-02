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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
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
 * 利用者の一覧の内部DB への問い合わせの回数の結合テスト（Intent 260930-user-admin の U3、NFR5.2・BR1.6）。
 *
 * <p>認証と認可の入口（アクセストークンの利用者の確かめ・管理者の印）も同じ要求の中で問い合わせるため、業務処理に入らずに返る要求
 * （page の誤りの 400）の回数を基準にし、一覧の要求との差を一覧の問い合わせの回数とする。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
@Import(AuthApiTestConfig.class)
class UserAdminListQueryCountIT {

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

    private UserAdminApi api;

    private UserAdminFixtures fixtures;

    private String admin;

    @BeforeEach
    void setUp() {
        api = new UserAdminApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        String email = "admin-" + UserAdminFixtures.marker() + "@example.com";
        fixtures.create(email, "数え 管理者", true);
        admin = fixtures.login(email);
    }

    private static List<SqlStatementCounter.Recorded> record(Supplier<HttpResponse<String>> request, int status) {
        SqlStatementCounter.start();
        HttpResponse<String> response = request.get();
        SqlStatementCounter.stop();
        assertThat(response.statusCode()).isEqualTo(status);
        return SqlStatementCounter.recorded();
    }

    private int listQueries(String query) {
        int baseline = record(() -> api.list(admin, "?page=0"), 400).size();
        List<SqlStatementCounter.Recorded> list = record(() -> api.list(admin, query), 200);
        return list.size() - baseline;
    }

    private String withUsers(int count) {
        String mark = UserAdminFixtures.marker();
        for (int i = 0; i < count; i++) {
            long userId = fixtures.create("count-" + i + "-" + mark + "@example.com", "数え " + i, false);
            if (i % 2 == 0) {
                fixtures.lockState(userId, 1, null);
            }
        }
        return mark;
    }

    @Test
    @DisplayName("a page of 20 rows reads the count, the rows and the lock states: three queries")
    void fullPage() {
        String mark = withUsers(20);

        assertThat(listQueries("?q=" + mark)).isEqualTo(3);
        assertThat(listQueries("")).isEqualTo(3);
        assertThat(record(() -> api.list(admin, "?q=" + mark), 200))
                .extracting(recorded -> SqlStatementCounter.kind(recorded.sql()))
                .as("ロックの状態は排他なしの1回で読む")
                .containsOnlyOnce("select login_attempt_states")
                .doesNotContain("select login_attempt_states for update");
    }

    @Test
    @DisplayName("the number of queries does not grow with the number of rows")
    void doesNotGrowWithRows() {
        String few = withUsers(2);
        String many = withUsers(20);

        assertThat(listQueries("?q=" + few))
                .isEqualTo(listQueries("?q=" + many))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("a page after the last one reads only the count, and a search hitting nothing likewise")
    void beyondLastPage() {
        String mark = withUsers(1);

        assertThat(listQueries("?q=" + mark + "&page=2")).isEqualTo(1);
        assertThat(listQueries("?q=" + UserAdminFixtures.marker())).isEqualTo(1);
    }
}
