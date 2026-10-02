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

import cherry.mastersmith.common.testsupport.RowLockHolder;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
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
 * UserAccount の排他の口が Busy を返したとき、呼び出し元が巻き戻しの印を付ければ、上限切れの前に同じトランザクションで書いたものが残らず、
 * 例外も出ないことの結合テスト（Intent 260930-user-admin の U3、{@code reliability-design.md} 5.3 の3行目、確かめ 4 の 4-1・4-3 の形）。
 */
@SpringBootTest
class UserAdminLockPortsIT {

    private static final String HOLD_USER = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAccountService service;

    @Autowired
    UserRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private long save(String name) {
        return repository
                .save(new User(
                        "ports-" + UUID.randomUUID() + "@example.com",
                        "$2a$04$hash",
                        false,
                        Instant.parse("2026-10-01T00:00:00Z"),
                        new Preferences(name, Language.JA, Theme.SYSTEM, FontSize.MD)))
                .getUserId();
    }

    private String nameOf(long userId) {
        return jdbc.queryForObject("SELECT display_name FROM users WHERE user_id = ?", String.class, userId);
    }

    @Test
    @DisplayName("a Busy admin-rows lock after an earlier write rolls the write back without an exception")
    void adminRowsBusyRollsBack() throws Exception {
        long written = save("書く前");
        long target = save("対象");
        AtomicReference<Object> lock = new AtomicReference<>();
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), HOLD_USER, target)) {
            tx.executeWithoutResult(status -> {
                service.updateProfile(written, new ProfileCommand("書いた後", "en"));
                AdminRowsLock result = service.lockAdminRowsInIdOrder(target);
                lock.set(result);
                if (result instanceof AdminRowsLock.Busy) {
                    status.setRollbackOnly();
                }
            });
        }

        assertThat(lock.get()).isEqualTo(new AdminRowsLock.Busy());
        assertThat(nameOf(written)).isEqualTo("書く前");
    }

    @Test
    @DisplayName("a Busy user-row lock after an earlier write rolls the write back without an exception")
    void userRowBusyRollsBack() throws Exception {
        long written = save("書く前");
        long target = save("対象");
        AtomicReference<Object> lock = new AtomicReference<>();
        try (RowLockHolder holder = RowLockHolder.hold(TestDatabase.url(tempDir), HOLD_USER, target)) {
            tx.executeWithoutResult(status -> {
                service.updateProfile(written, new ProfileCommand("書いた後", "en"));
                UserRowLock result = service.lockUserRow(target);
                lock.set(result);
                if (result instanceof UserRowLock.Busy) {
                    status.setRollbackOnly();
                }
            });
        }

        assertThat(lock.get()).isEqualTo(new UserRowLock.Busy());
        assertThat(nameOf(written)).isEqualTo("書く前");
    }

    @Test
    @DisplayName(
            "a free admin-rows lock reads the target summary and active admins after locking, and setAdmin commits")
    void acquiredAndSetAdmin() {
        long target = save("印 対象");

        AdminRowsLock lock = tx.execute(status -> {
            AdminRowsLock result = service.lockAdminRowsInIdOrder(target);
            service.setAdmin(target, true);
            return result;
        });

        assertThat(lock).isInstanceOfSatisfying(AdminRowsLock.Locked.class, locked -> {
            assertThat(locked.target())
                    .get()
                    .extracting(UserAdminSummary::admin)
                    .isEqualTo(false);
            assertThat(locked.activeAdminIds()).doesNotContain(target);
        });
        assertThat(jdbc.queryForObject("SELECT admin_flag FROM users WHERE user_id = ?", Boolean.class, target))
                .isTrue();
    }
}
