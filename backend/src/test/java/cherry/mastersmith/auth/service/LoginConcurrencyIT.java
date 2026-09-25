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

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
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

/**
 * 同時のログインの失敗でも取りこぼさず、ほかの利用者を待たせないことの結合テスト（NFR1.5、NFR9.1、BR3.8）。
 *
 * <p>ロックの状態の行が無い利用者の同時の初めてのログインが、内部の失敗（500）にならないことも確かめる（260924-followup-fixes の
 * FR2）。同時の本数（10）より多いスレッドを用意し、監査の2本目を含めても接続プールの上限（30）の内側に収める。
 */
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

    private static final int SIMULTANEOUS_LOGINS = 10;

    private final ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_LOGINS + 2);

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

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    /** 利用者のロックの状態の行を消し、初めてのログインの前の状態にする。 */
    private String newUserWithoutRow() {
        String email = newUser();
        jdbc.update("DELETE FROM login_attempt_states WHERE subject_id = ?", userId(email));
        return email;
    }

    private int rows(String email) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_attempt_states WHERE subject_id = ?", Integer.class, userId(email));
    }

    private int auditEvents(String email, String eventType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE entered_email = ? AND event_type = ?",
                Integer.class,
                email,
                eventType);
    }

    /** ログインのスレッドが DB（H2）の中で待ちに入るまで、期限つきで見張る。 */
    private static boolean waitsInDatabase(Thread thread, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            boolean waiting = state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
            if (waiting
                    && Arrays.stream(thread.getStackTrace())
                            .anyMatch(frame -> frame.getClassName().startsWith("org.h2."))) {
                return true;
            }
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
        }
        return false;
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

    @Test
    @DisplayName("simultaneous first logins of a user without a row all succeed")
    void simultaneousFirstLogins() throws Exception {
        String email = newUserWithoutRow();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IssuedTokens>> results = new ArrayList<>();
        for (int i = 0; i < SIMULTANEOUS_LOGINS; i++) {
            results.add(executor.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                return loginService.login(new LoginCommand(email, new Password(PASSWORD)), CLIENT);
            }));
        }
        start.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<IssuedTokens> result : results) {
            try {
                assertThat(result.get(60, TimeUnit.SECONDS).user().email()).isEqualTo(email);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        assertThat(failures).isEmpty();
        assertThat(rows(email)).isEqualTo(1);
        assertThat(state(email).get("CONSECUTIVE_FAILURES")).isEqualTo(0);
        assertThat(auditEvents(email, "LOGIN_SUCCEEDED")).isEqualTo(SIMULTANEOUS_LOGINS);
        assertThat(auditEvents(email, "LOGIN_FAILED")).isZero();
    }

    @Test
    @DisplayName("a login waiting for a row another login is creating succeeds after that commit")
    void waitsForRowBeingCreated() throws Exception {
        String email = newUserWithoutRow();
        long id = userId(email);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> creator = executor.submit(() -> tx.executeWithoutResult(status -> {
            jdbc.update(
                    "INSERT INTO login_attempt_states (subject_id, consecutive_failures, locked_until)"
                            + " VALUES (?, 0, NULL)",
                    id);
            inserted.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(inserted.await(30, TimeUnit.SECONDS)).isTrue();

        AtomicReference<Thread> loginThread = new AtomicReference<>();
        CountDownLatch loginStarted = new CountDownLatch(1);
        Future<IssuedTokens> login = executor.submit(() -> {
            loginThread.set(Thread.currentThread());
            loginStarted.countDown();
            return loginService.login(new LoginCommand(email, new Password(PASSWORD)), CLIENT);
        });
        assertThat(loginStarted.await(30, TimeUnit.SECONDS)).isTrue();
        boolean waited = waitsInDatabase(loginThread.get(), Duration.ofSeconds(30));
        release.countDown();
        creator.get(30, TimeUnit.SECONDS);

        assertThat(waited).isTrue();
        assertThat(login.get(60, TimeUnit.SECONDS).user().email()).isEqualTo(email);
        assertThat(rows(email)).isEqualTo(1);
        assertThat(state(email).get("CONSECUTIVE_FAILURES")).isEqualTo(0);
        assertThat(auditEvents(email, "LOGIN_SUCCEEDED")).isEqualTo(1);
    }

    @Test
    @DisplayName("four simultaneous first failures without a row are counted without locking")
    void fourFirstFailuresDoNotLock() throws Exception {
        String email = newUserWithoutRow();

        failConcurrently(email, 4);

        assertThat(rows(email)).isEqualTo(1);
        assertThat(state(email).get("CONSECUTIVE_FAILURES")).isEqualTo(4);
        assertThat(state(email).get("LOCKED_UNTIL")).isNull();
        assertThat(auditEvents(email, "LOGIN_FAILED")).isEqualTo(4);
    }

    @Test
    @DisplayName("five simultaneous first failures without a row lock the account and reject the right password")
    void fiveFirstFailuresLock() throws Exception {
        String email = newUserWithoutRow();

        failConcurrently(email, 5);

        assertThat(rows(email)).isEqualTo(1);
        assertThat(state(email).get("CONSECUTIVE_FAILURES")).isEqualTo(5);
        assertThat(state(email).get("LOCKED_UNTIL")).isNotNull();
        assertThat(auditEvents(email, "LOGIN_FAILED")).isEqualTo(5);
        assertThatThrownBy(() -> loginService.login(new LoginCommand(email, new Password(PASSWORD)), CLIENT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(AuthProblemTypes.AUTHENTICATION_FAILED));
        assertThat(auditEvents(email, "LOGIN_FAILED")).isEqualTo(6);
    }
}
