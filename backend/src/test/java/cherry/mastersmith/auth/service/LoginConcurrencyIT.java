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

import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** 同時のログインの失敗でも取りこぼさず、ほかの利用者を待たせないことの結合テスト（NFR1.5、NFR9.1、BR3.8）。 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class LoginConcurrencyIT {

    private static final String PASSWORD = "正しいパスワード-1234";

    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "IT", null);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    LoginService loginService;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    LoginAttemptStateRepository attempts;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private String newUser() {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        userAccountService.createUser(email, new Password(PASSWORD), false);
        return email;
    }

    private void failConcurrently(String email, int count) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(executor.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                try {
                    loginService.login(new LoginCommand(email, new Password("まちがい")), CLIENT);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        for (Future<Boolean> result : results) {
            assertThat(result.get(60, TimeUnit.SECONDS)).isFalse();
        }
    }

    private Map<String, Object> state(String email) {
        return jdbc.queryForMap(
                "SELECT s.consecutive_failures, s.locked_until FROM login_attempt_states s"
                        + " JOIN users u ON u.user_id = s.subject_id WHERE u.email = ?",
                email);
    }

    @Test
    @DisplayName("five simultaneous failures are all counted and lock the account")
    void fiveLock() throws Exception {
        String email = newUser();

        failConcurrently(email, 5);

        assertThat(state(email).get("CONSECUTIVE_FAILURES")).isEqualTo(5);
        assertThat(state(email).get("LOCKED_UNTIL")).isNotNull();
    }

    @Test
    @DisplayName("four simultaneous failures are counted without locking")
    void fourDoNotLock() throws Exception {
        String email = newUser();

        failConcurrently(email, 4);

        assertThat(state(email).get("CONSECUTIVE_FAILURES")).isEqualTo(4);
        assertThat(state(email).get("LOCKED_UNTIL")).isNull();
    }

    @Test
    @DisplayName("while one user's row is locked another user's login does not wait")
    void otherUserDoesNotWait() throws Exception {
        String held = newUser();
        String other = newUser();
        long heldId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, held);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = executor.submit(() -> tx.executeWithoutResult(status -> {
            attempts.lockForUpdate(heldId).orElseThrow();
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        locked.await(30, TimeUnit.SECONDS);

        long start = System.nanoTime();
        IssuedTokens tokens = loginService.login(new LoginCommand(other, new Password(PASSWORD)), CLIENT);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        release.countDown();
        holder.get(30, TimeUnit.SECONDS);

        assertThat(tokens.user().email()).isEqualTo(other);
        assertThat(elapsedMillis).isLessThan(2500L);
    }
}
