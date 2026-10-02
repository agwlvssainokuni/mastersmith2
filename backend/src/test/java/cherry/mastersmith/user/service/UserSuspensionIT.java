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
package cherry.mastersmith.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
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

/** 停止の状態の口（契約 C1 の {@code isSuspended}・{@code setSuspended}）の結合テスト（Intent 260930-user-admin の U1）。 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class UserSuspensionIT {

    private static final String PASSWORD = "正しいパスワード-1234";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAccountService service;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private long createUser(boolean admin) {
        return TestUserAccounts.create(service, "suspension-" + UUID.randomUUID() + "@example.com", PASSWORD, admin);
    }

    private boolean storedSuspended(long userId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT suspended FROM users WHERE user_id = ?", Boolean.class, userId));
    }

    @Test
    @DisplayName("setSuspended outside a transaction is refused and writes nothing")
    void requiresTransaction() {
        long userId = createUser(false);

        assertThatThrownBy(() -> service.setSuspended(userId, true))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(storedSuspended(userId)).isFalse();
    }

    @Test
    @DisplayName("rolling back the caller rolls back the suspension, and committing keeps it")
    void followsTheCallerTransaction() {
        long userId = createUser(false);

        tx.executeWithoutResult(status -> {
            service.setSuspended(userId, true);
            status.setRollbackOnly();
        });
        assertThat(storedSuspended(userId)).isFalse();
        assertThat(service.isSuspended(userId)).isFalse();

        tx.executeWithoutResult(status -> service.setSuspended(userId, true));
        assertThat(storedSuspended(userId)).isTrue();
        assertThat(service.isSuspended(userId)).isTrue();
    }

    @Test
    @DisplayName("reads after a write in the same transaction return the written value even after an earlier read")
    void readAfterWriteInTheSameTransaction() {
        long userId = createUser(false);

        boolean[] read = tx.execute(status -> {
            boolean before = service.findById(userId).orElseThrow().suspended();
            service.setSuspended(userId, true);
            boolean suspendedFlag = service.isSuspended(userId);
            boolean suspendedSummary = service.findById(userId).orElseThrow().suspended();
            service.setSuspended(userId, false);
            boolean resumedFlag = service.isSuspended(userId);
            boolean resumedSummary = service.findById(userId).orElseThrow().suspended();
            return new boolean[] {before, suspendedFlag, suspendedSummary, resumedFlag, resumedSummary};
        });

        assertThat(read).containsExactly(false, true, true, false, false);
    }

    @Test
    @DisplayName("an unknown user fails with IllegalStateException and the caller transaction is rolled back")
    void unknownUser() {
        long userId = createUser(false);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    service.setSuspended(userId, true);
                    service.setSuspended(Long.MAX_VALUE, true);
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId=" + Long.MAX_VALUE);
        assertThatThrownBy(() -> service.isSuspended(Long.MAX_VALUE)).isInstanceOf(IllegalStateException.class);

        // 先に止めた利用者も、呼び出し元ごと巻き戻る。
        assertThat(storedSuspended(userId)).isFalse();
    }

    @Test
    @DisplayName("suspending and resuming changes neither the admin flag, the name and display values nor the lock row")
    void otherStateIsUnchanged() {
        long userId = createUser(true);
        jdbc.update(
                "UPDATE login_attempt_states SET consecutive_failures = 3, locked_until = ? WHERE subject_id = ?",
                OffsetDateTime.parse("2026-10-02T09:00:00Z"),
                userId);
        String columns = "SELECT email, password_hash, admin_flag, display_name, language, theme, font_size"
                + " FROM users WHERE user_id = ?";
        String lockRow = "SELECT consecutive_failures, locked_until FROM login_attempt_states WHERE subject_id = ?";
        Map<String, Object> userBefore = jdbc.queryForMap(columns, userId);
        Map<String, Object> lockBefore = jdbc.queryForMap(lockRow, userId);

        tx.executeWithoutResult(status -> service.setSuspended(userId, true));
        Map<String, Object> userSuspended = jdbc.queryForMap(columns, userId);
        Map<String, Object> lockSuspended = jdbc.queryForMap(lockRow, userId);
        tx.executeWithoutResult(status -> service.setSuspended(userId, false));

        assertThat(userSuspended).isEqualTo(userBefore);
        assertThat(lockSuspended).isEqualTo(lockBefore);
        assertThat(jdbc.queryForMap(columns, userId)).isEqualTo(userBefore);
        assertThat(jdbc.queryForMap(lockRow, userId)).isEqualTo(lockBefore);
        assertThat(lockBefore).containsEntry("CONSECUTIVE_FAILURES", 3);
    }
}
