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
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.Theme;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の作成（契約 C2、BR5.2・BR5.3、{@code reliability-design.md} 4節）の結合テスト（組み込みの H2）。
 *
 * <p>同じメールアドレスの2つの作成の重なりは、登録済みの確かめとハッシュの保存の間（ハッシュの計算）でテストの中の待ち合わせをして
 * 確実に作る（{@code sleep} に頼らない）。
 */
@SpringBootTest
@Import(UserCreationIT.BarrierConfig.class)
class UserCreationIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAccountService service;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    BarrierPasswordEncoder encoder;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        encoder.barrier(null);
        executor.shutdownNow();
    }

    /** ハッシュの計算で待ち合わせられるパスワードのハッシュの仕組み（cost 4）。 */
    static class BarrierPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate = new BCryptPasswordEncoder(4);

        private volatile CyclicBarrier barrier;

        void barrier(CyclicBarrier value) {
            this.barrier = value;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            CyclicBarrier current = barrier;
            if (current != null) {
                try {
                    current.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new IllegalStateException("待ち合わせが成り立たなかった", e);
                }
            }
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return delegate.matches(rawPassword, encodedPassword);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BarrierConfig {

        @Bean
        @Primary
        BarrierPasswordEncoder barrierPasswordEncoder() {
            return new BarrierPasswordEncoder();
        }
    }

    private static String uniqueEmail() {
        return "create-" + UUID.randomUUID() + "@example.com";
    }

    private static NewUser newUser(String email) {
        return new NewUser(email, "作成の テスト", new Password("作成のテストのパスワード"), Language.EN, Theme.DARK, FontSize.LG, false);
    }

    private int users(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
    }

    private int lockRowsFor(String email) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_attempt_states s JOIN users u ON s.subject_id = u.user_id WHERE u.email = ?",
                Integer.class,
                email);
    }

    @Test
    @DisplayName("the creation joins the caller's transaction and leaves neither the user nor the lock row on rollback")
    void joinsCallerTransaction() {
        String email = uniqueEmail();

        CreateUserResult result = tx.execute(status -> {
            CreateUserResult created = service.createUser(newUser(email));
            status.setRollbackOnly();
            return created;
        });

        assertThat(result).isInstanceOf(CreateUserResult.Created.class);
        long userId = ((CreateUserResult.Created) result).userId();
        assertThat(users(email)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM login_attempt_states WHERE subject_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    @DisplayName("a created user carries the stripped name and the values, and has one lock row")
    void createsWithValues() {
        String email = uniqueEmail();

        CreateUserResult result = service.createUser(newUser(email));

        assertThat(result).isInstanceOf(CreateUserResult.Created.class);
        assertThat(jdbc.queryForMap(
                        "SELECT display_name, language, theme, font_size, admin_flag FROM users WHERE email = ?",
                        email))
                .containsEntry("DISPLAY_NAME", "作成の テスト")
                .containsEntry("LANGUAGE", "en")
                .containsEntry("THEME", "dark")
                .containsEntry("FONT_SIZE", "lg")
                .containsEntry("ADMIN_FLAG", false);
        assertThat(lockRowsFor(email)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "an own-transaction creation with a registered email returns EmailAlreadyUsed without UnexpectedRollback")
    void ownTransactionEmailAlreadyUsed() {
        String email = uniqueEmail();
        service.createUser(newUser(email));

        CreateUserResult again = service.createUser(newUser(email.toUpperCase(java.util.Locale.ROOT)));

        assertThat(again).isEqualTo(new CreateUserResult.EmailAlreadyUsed());
        assertThat(users(email)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "a caller that tries to commit after EmailAlreadyUsed gets UnexpectedRollback, so callers must roll back")
    void callerMustRollBack() {
        String email = uniqueEmail();
        service.createUser(newUser(email));

        assertThatThrownBy(() -> tx.execute(status -> service.createUser(newUser(email))))
                .isInstanceOf(UnexpectedRollbackException.class);
        assertThat(users(email)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "two overlapping creations of the same email make one Created and one EmailAlreadyUsed with one lock row")
    void overlappingCreations() throws Exception {
        String email = uniqueEmail();
        encoder.barrier(new CyclicBarrier(2));

        Future<CreateUserResult> first = executor.submit(() -> service.createUser(newUser(email)));
        Future<CreateUserResult> second = executor.submit(() -> service.createUser(newUser(email)));
        List<CreateUserResult> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

        assertThat(results)
                .filteredOn(CreateUserResult.Created.class::isInstance)
                .hasSize(1);
        assertThat(results)
                .filteredOn(CreateUserResult.EmailAlreadyUsed.class::isInstance)
                .hasSize(1);
        assertThat(users(email)).isEqualTo(1);
        assertThat(lockRowsFor(email)).isEqualTo(1);
    }
}
