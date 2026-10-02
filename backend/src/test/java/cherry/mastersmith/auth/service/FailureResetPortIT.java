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

import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 失敗回数を戻す2段の口の結合テスト（Intent 260930-user-admin の U3、FS の D6、BR4.5、{@code reliability-design.md} 5.3 の3行目）。
 *
 * <p>1段目が Busy を返したとき、呼び出し元が巻き戻しの印を付ければ上限切れの前に書いたものが残らず例外も出ないこと、2段目が行を作らない
 * ことを確かめる。
 */
@SpringBootTest
class FailureResetPortIT {

    private static final String HOLD_ROW =
            "SELECT subject_id FROM login_attempt_states WHERE subject_id = ? FOR UPDATE";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    LockAdministrationService service;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private long newUser() {
        String email = "reset-port-" + UUID.randomUUID() + "@example.com";
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES (?, 'x', FALSE, ?, ?)",
                email,
                OffsetDateTime.now(),
                email);
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    private void lockState(long userId, int failures) {
        jdbc.update(
                "MERGE INTO login_attempt_states (subject_id, consecutive_failures, locked_until) KEY (subject_id)"
                        + " VALUES (?, ?, NULL)",
                userId,
                failures);
    }

    private int failures(long userId) {
        return jdbc.queryForObject(
                "SELECT consecutive_failures FROM login_attempt_states WHERE subject_id = ?", Integer.class, userId);
    }

    private int rows(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_attempt_states WHERE subject_id = ?", Integer.class, userId);
    }

    @Test
    @DisplayName("a Busy first step after an earlier write rolls the write back without an exception")
    void busyRollsBack() throws Exception {
        long written = newUser();
        long target = newUser();
        lockState(written, 3);
        lockState(target, 2);
        AtomicReference<LoginFailureResetPreparation> prepared = new AtomicReference<>();
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), HOLD_ROW, target)) {
            tx.executeWithoutResult(status -> {
                service.completeFailureReset(written);
                LoginFailureResetPreparation result = service.prepareFailureReset(target);
                prepared.set(result);
                if (result instanceof LoginFailureResetPreparation.Busy) {
                    status.setRollbackOnly();
                }
            });
        }

        assertThat(prepared.get()).isEqualTo(new LoginFailureResetPreparation.Busy());
        assertThat(failures(written)).isEqualTo(3);
        assertThat(failures(target)).isEqualTo(2);
    }

    @Test
    @DisplayName("Ready then the second step writes zero, and NothingToReset for a user without a row")
    void readyAndComplete() {
        long target = newUser();
        long withoutRow = newUser();
        jdbc.update("DELETE FROM login_attempt_states WHERE subject_id = ?", withoutRow);
        lockState(target, 2);

        LoginFailureResetPreparation result = tx.execute(status -> {
            LoginFailureResetPreparation ready = service.prepareFailureReset(target);
            service.completeFailureReset(target);
            return ready;
        });
        LoginFailureResetPreparation missing = tx.execute(status -> service.prepareFailureReset(withoutRow));

        assertThat(result).isEqualTo(new LoginFailureResetPreparation.Ready());
        assertThat(failures(target)).isZero();
        assertThat(missing).isEqualTo(new LoginFailureResetPreparation.NothingToReset());
        assertThat(rows(withoutRow)).isZero();
    }

    @Test
    @DisplayName("the second step never creates a row for a user without one")
    void completeCreatesNoRow() {
        long withoutRow = newUser();
        jdbc.update("DELETE FROM login_attempt_states WHERE subject_id = ?", withoutRow);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.completeFailureReset(withoutRow)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows(withoutRow)).isZero();
    }
}
