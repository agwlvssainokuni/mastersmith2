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

import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.auth.service.LoginCommand;
import cherry.mastersmith.auth.service.LoginService;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.service.RevokeAllResult;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.domain.RejectionReason;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 業務処理の層を直接呼ぶ5つの操作の結合テスト（Intent 260930-user-admin の U3、FS の 2.10、FR4.4、AC2.1.11・AC3.1.9、NFR9.5、U1 の
 * レビュー R-03）。
 *
 * <p>API の1件ずつの操作では、操作する管理者が有効な管理者のため最後の有効な管理者の拒否は起きない。そこで操作した人をロック中・停止中の
 * 管理者 C にして業務処理を直接呼ぶ。内部DB はクラスで共有するため、各テストの初めに既存の管理者の印をすべて外し、そのテストで作った
 * 管理者だけを数える。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, UserAdminOperationsIT.FailingRevocationConfig.class})
class UserAdminOperationsIT {

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.70", "IT", null);

    private static final Instant FAR_FUTURE = Instant.parse("2999-01-01T00:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAdminService service;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    FailingRevocations revocations;

    @Autowired
    LoginService loginService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    private UserAdminFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new UserAdminFixtures(userAccountService, revocations, transactionManager, jdbc, 0);
        fixtures.demoteAllAdmins();
    }

    @AfterEach
    void restoreRevocations() {
        revocations.failFor(null);
    }

    private long user(String label, boolean admin) {
        return fixtures.create(label + "-" + UserAdminFixtures.marker() + "@example.com", "操作 " + label, admin);
    }

    private String emailOf(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, userId);
    }

    private void login(long userId) {
        loginService.login(
                new LoginCommand(emailOf(userId), new Password(UserAdminFixtures.PASSWORD)),
                new ClientInfo("127.0.0.1", "IT", null));
    }

    @Test
    @DisplayName("a locked admin is still an active admin, so it may revoke or suspend the only other admin")
    void lockedOperatorIsCounted() {
        long b = user("b", true);
        long c = user("c", true);
        fixtures.lockState(c, 5, FAR_FUTURE);

        assertThat(service.revokeAdmin(c, ORIGIN, b)).isEqualTo(new OperationResult.Done());
        assertThat(fixtures.userColumns(b)).containsEntry("ADMIN_FLAG", false);

        long b2 = user("b2", true);
        assertThat(service.suspend(c, ORIGIN, b2)).isEqualTo(new OperationResult.Done());
        assertThat(fixtures.userColumns(b2)).containsEntry("SUSPENDED", true);
        assertThat(fixtures.activeAdminIds()).containsExactly(c);
    }

    @Test
    @DisplayName("a suspended admin operating on the only active admin is LAST_ACTIVE_ADMIN and nothing changes")
    void suspendedOperatorHitsLastAdmin() {
        long b = user("b", true);
        long c = user("c", true);
        fixtures.suspend(c);
        Map<String, Object> before = fixtures.userColumns(b);

        assertThat(service.revokeAdmin(c, ORIGIN, b))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.LAST_ACTIVE_ADMIN));
        assertThat(service.suspend(c, ORIGIN, b))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.LAST_ACTIVE_ADMIN));

        assertThat(fixtures.userColumns(b)).isEqualTo(before);
        assertThat(fixtures.activeAdminIds()).containsExactly(b);
    }

    @Test
    @DisplayName("a failure while revoking the tokens rolls back the suspension and the tokens together")
    void suspendRollsBackAsOne() {
        long a = user("a", true);
        long t = user("t", false);
        login(t);
        login(t);
        revocations.failFor(t);

        assertThatThrownBy(() -> service.suspend(a, ORIGIN, t))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("試しの失敗");

        assertThat(fixtures.userColumns(t)).containsEntry("SUSPENDED", false);
        assertThat(fixtures.activeRefreshTokens(t)).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "after suspending, the flag, name and language are unchanged and suspension and revocation are committed")
    void suspendKeepsOtherColumns() {
        long a = user("a", true);
        long t = user("t", true);
        login(t);
        Map<String, Object> before = fixtures.userColumns(t);

        assertThat(service.suspend(a, ORIGIN, t)).isEqualTo(new OperationResult.Done());

        Map<String, Object> after = fixtures.userColumns(t);
        assertThat(after)
                .containsEntry("ADMIN_FLAG", before.get("ADMIN_FLAG"))
                .containsEntry("DISPLAY_NAME", before.get("DISPLAY_NAME"))
                .containsEntry("LANGUAGE", before.get("LANGUAGE"))
                .containsEntry("SUSPENDED", true);
        assertThat(fixtures.activeRefreshTokens(t)).isZero();
    }

    @Test
    @DisplayName("resuming and resetting through the service change only their own columns")
    void resumeAndReset() {
        long a = user("a", true);
        long t = user("t", false);
        fixtures.suspend(t);
        fixtures.lockState(t, 5, FAR_FUTURE);

        assertThat(service.resume(a, ORIGIN, t)).isEqualTo(new OperationResult.Done());
        assertThat(fixtures.userColumns(t)).containsEntry("SUSPENDED", false);
        assertThat(fixtures.lockColumns(t)).containsEntry("CONSECUTIVE_FAILURES", 5);

        assertThat(service.resetLoginFailures(a, ORIGIN, t)).isEqualTo(new OperationResult.Done());
        assertThat(fixtures.lockColumns(t))
                .containsEntry("CONSECUTIVE_FAILURES", 0)
                .containsEntry("LOCKED_UNTIL", null);
        assertThat(service.resetLoginFailures(a, ORIGIN, t))
                .isEqualTo(new OperationResult.Rejected(RejectionReason.NO_CHANGE));
    }

    /** 指定した利用者のまとめての無効化だけを失敗させる、C1 の口の差し替え（止める操作の途中の失敗を起こすため）。 */
    static class FailingRevocations extends RefreshTokenRevocationService {

        private volatile Long failFor;

        FailingRevocations(RefreshTokenRepository repository, Clock clock) {
            super(repository, clock);
        }

        /**
         * 失敗させる利用者を決める（代理を通して本体に届くよう、項目ではなくメソッドで渡す）。
         *
         * @param userId 失敗させる利用者 ID（null なら失敗させない）
         */
        public void failFor(Long userId) {
            failFor = userId;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public RevokeAllResult revokeAllRefreshTokens(long userId) {
            Long target = failFor;
            if (target != null && target == userId) {
                throw new IllegalStateException("試しの失敗");
            }
            return super.revokeAllRefreshTokens(userId);
        }
    }

    /** C1 の口の差し替えの設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingRevocationConfig {

        @Bean
        @Primary
        FailingRevocations failingRevocations(RefreshTokenRepository repository, Clock clock) {
            return new FailingRevocations(repository, clock);
        }
    }
}
