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
package cherry.mastersmith.useradmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.service.IssuedTokens;
import cherry.mastersmith.auth.service.LoginCommand;
import cherry.mastersmith.auth.service.LoginService;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.domain.AdminOperation;
import cherry.mastersmith.useradmin.testsupport.TestUserAdminBarrier;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 止める操作と、同じ利用者のログインの重なりの結合テスト（Intent 260930-user-admin の U3、{@code reliability-design.md} 確かめ 3 の本番版、
 * BR3.4・BR4.3、NFR4.3）。
 *
 * <p>止める操作を、管理者の行と対象の行を排他した後の待ち合わせの口で止めている間に、同じ利用者のログイン（照合・ロックの状態の行の
 * 排他・リフレッシュトークンの追記）が、利用者の行の排他を待たずに通ることを確かめる。決めた側の動作（M8 B の隙）として、止める前に
 * 確定したログインのトークンは、止める操作の確定でまとめて無効になることも固定する。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestUserAdminBarrier.Config.class})
class SuspendWhileLoginIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.91", "IT", null);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAdminService service;

    @Autowired
    LoginService loginService;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    RefreshTokenRevocationService revocations;

    @Autowired
    TestUserAdminBarrier barrier;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        barrier.reset();
    }

    @Test
    @DisplayName(
            "a login of the user being suspended passes without waiting and its token is revoked by the suspension")
    void loginPassesWhileSuspendWaits() throws Exception {
        UserAdminFixtures fixtures =
                new UserAdminFixtures(userAccountService, revocations, transactionManager, jdbc, 0);
        long admin = fixtures.create("admin-" + UserAdminFixtures.marker() + "@example.com", "止める 管理者", true);
        String email = "suspend-" + UserAdminFixtures.marker() + "@example.com";
        long target = fixtures.create(email, "止める 対象", false);
        CompletableFuture<IssuedTokens> login = barrier.whileCounting(
                AdminOperation.SUSPEND,
                target,
                () -> loginService.login(
                        new LoginCommand(email, new Password(UserAdminFixtures.PASSWORD)),
                        new ClientInfo("127.0.0.1", "IT", null)));

        OperationResult suspended = service.suspend(admin, ORIGIN, target);

        // ログインは止める操作を数える直前で止めている間に終わった（利用者の行の排他を待たなかった）。
        assertThat(barrier.secondDoneAtRelease()).isTrue();
        IssuedTokens tokens = login.get(30, TimeUnit.SECONDS);
        assertThat(tokens.user().userId()).isEqualTo(target);
        assertThat(suspended).isEqualTo(new OperationResult.Done());
        assertThat(fixtures.userColumns(target)).containsEntry("SUSPENDED", true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, target))
                .isEqualTo(1);
        assertThat(fixtures.activeRefreshTokens(target)).isZero();
    }
}
