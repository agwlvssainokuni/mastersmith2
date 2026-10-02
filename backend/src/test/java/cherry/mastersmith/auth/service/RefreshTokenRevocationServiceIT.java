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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.testsupport.TestUserSuspension;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** リフレッシュトークンのまとめての無効化（契約 C1）の結合テスト（Intent 260930-user-admin の U1、BR5.1〜BR5.4）。 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class RefreshTokenRevocationServiceIT {

    private static final String PASSWORD = "正しいパスワード-1234";

    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "IT", null);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RefreshTokenRevocationService revocationService;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    LoginService loginService;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private TestUserSuspension suspension;

    @BeforeEach
    void prepare() {
        suspension = new TestUserSuspension(tx, userAccountService, revocationService);
    }

    /** 利用者を作り、2回ログインして未無効のリフレッシュトークンを2つ作る。 */
    private long userWithTwoTokens() {
        String email = "revoke-" + UUID.randomUUID() + "@example.com";
        long userId = TestUserAccounts.create(userAccountService, email, PASSWORD, false);
        loginService.login(new LoginCommand(email, new Password(PASSWORD)), CLIENT);
        loginService.login(new LoginCommand(email, new Password(PASSWORD)), CLIENT);
        return userId;
    }

    private int activeTokens(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private boolean storedSuspended(long userId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT suspended FROM users WHERE user_id = ?", Boolean.class, userId));
    }

    @Test
    @DisplayName("revoking outside a transaction is refused and revokes nothing")
    void requiresTransaction() {
        long userId = userWithTwoTokens();

        assertThatThrownBy(() -> revocationService.revokeAllRefreshTokens(userId))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(activeTokens(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("rolling back the caller rolls back both the suspension and the revocation")
    void rollbackUndoesBoth() {
        long userId = userWithTwoTokens();

        RevokeAllResult result = tx.execute(status -> {
            userAccountService.setSuspended(userId, true);
            RevokeAllResult revoked = revocationService.revokeAllRefreshTokens(userId);
            status.setRollbackOnly();
            return revoked;
        });

        assertThat(result).isEqualTo(new RevokeAllResult(2));
        assertThat(storedSuspended(userId)).isFalse();
        assertThat(activeTokens(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("committing keeps both the suspension and the revocation, and other users keep their tokens")
    void commitKeepsBoth() {
        long userId = userWithTwoTokens();
        long other = userWithTwoTokens();

        int revoked = suspension.suspend(userId);

        assertThat(revoked).isEqualTo(2);
        assertThat(storedSuspended(userId)).isTrue();
        assertThat(activeTokens(userId)).isZero();
        assertThat(activeTokens(other)).isEqualTo(2);
        assertThat(storedSuspended(other)).isFalse();
    }

    @Test
    @DisplayName("lifting the suspension does not bring the revoked tokens back")
    void resumeDoesNotRestoreTokens() {
        long userId = userWithTwoTokens();
        suspension.suspend(userId);

        suspension.resume(userId);

        assertThat(storedSuspended(userId)).isFalse();
        assertThat(activeTokens(userId)).isZero();
        // 止め直しても、無効にする行はもう無い（0 件も成功）。
        assertThat(suspension.suspend(userId)).isZero();
    }
}
