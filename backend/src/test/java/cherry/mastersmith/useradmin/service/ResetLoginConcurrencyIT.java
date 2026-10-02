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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.service.LoginCommand;
import cherry.mastersmith.auth.service.LoginService;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.TestLoginAttemptBarrier;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 失敗回数を戻す操作と、しきい値に届くログインの重なりの結合テスト（Intent 260930-user-admin の U3、AC4.1.5、FR5.4、BR3.4・BR3.6、
 * NFR4.2、FS の 2.9）。
 *
 * <p>失敗回数はしきい値−1（設定の既定 5 から 4）。どちらが先にロックの状態の行を排他しても、2つを順に行った結果の一方に一致し、5xx に
 * ならないことを確かめる。重なりは待ち合わせの口（{@link TestLoginAttemptBarrier}）で1つ目を行の排他の直後に止めて作り、時刻は注入した
 * 時計（{@code AuthApiTestConfig} の {@code MutableClock}）で動かす。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestLoginAttemptBarrier.Config.class})
class ResetLoginConcurrencyIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.90", "IT", null);

    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "IT", null);

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
    TestLoginAttemptBarrier loginBarrier;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    private UserAdminFixtures fixtures;

    private long admin;

    @BeforeEach
    void setUp() {
        fixtures = new UserAdminFixtures(userAccountService, revocations, transactionManager, jdbc, 0);
        admin = fixtures.create("admin-" + UserAdminFixtures.marker() + "@example.com", "戻す 管理者", true);
    }

    @AfterEach
    void tearDown() {
        loginBarrier.reset();
    }

    /** 失敗回数がしきい値−1 の利用者を作り、メールアドレスを返す。 */
    private String userAtThresholdMinusOne() {
        String email = "reset-" + UserAdminFixtures.marker() + "@example.com";
        long id = fixtures.create(email, "戻す 対象", false);
        fixtures.lockState(id, 4, null);
        return email;
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    /** 誤ったパスワードでログインし、業務の失敗（AUTHENTICATION_FAILED）だけを受け止める（5xx は外へ出す）。 */
    private boolean failLogin(String email) {
        assertThatThrownBy(() -> loginService.login(new LoginCommand(email, new Password("まちがい-0000")), CLIENT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(AuthProblemTypes.AUTHENTICATION_FAILED));
        return true;
    }

    @Test
    @DisplayName("when the reset locks first, the waiting login counts from zero: one failure and no lock")
    void resetFirst() throws Exception {
        String email = userAtThresholdMinusOne();
        long target = idOf(email);
        CompletableFuture<Boolean> login = loginBarrier.whileLocked(target, () -> failLogin(email));

        OperationResult reset = service.resetLoginFailures(admin, ORIGIN, target);

        assertThat(loginBarrier.secondDoneAtRelease()).isFalse();
        assertThat(reset).isEqualTo(new OperationResult.Done());
        assertThat(login.get(30, TimeUnit.SECONDS)).isTrue();
        assertThat(fixtures.lockColumns(target))
                .containsEntry("CONSECUTIVE_FAILURES", 1)
                .containsEntry("LOCKED_UNTIL", null);
    }

    @Test
    @DisplayName("when the login locks first and reaches the threshold, the waiting reset clears failures and the lock")
    void loginFirst() throws Exception {
        String email = userAtThresholdMinusOne();
        long target = idOf(email);
        CompletableFuture<OperationResult> reset =
                loginBarrier.whileLocked(target, () -> service.resetLoginFailures(admin, ORIGIN, target));

        failLogin(email);

        assertThat(loginBarrier.secondDoneAtRelease()).isFalse();
        assertThat(reset.get(30, TimeUnit.SECONDS)).isEqualTo(new OperationResult.Done());
        assertThat(fixtures.lockColumns(target))
                .containsEntry("CONSECUTIVE_FAILURES", 0)
                .containsEntry("LOCKED_UNTIL", null);
    }
}
