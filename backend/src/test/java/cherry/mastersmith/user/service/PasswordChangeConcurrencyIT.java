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

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.testsupport.TestPasswordChangeBarrier;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 同時のパスワードの変更（Q3 A の条件つきの更新、{@code reliability-design.md} 2節）の結合テスト（組み込みの H2）。
 *
 * <p>照合の後・書き込みの前に、同じ利用者の別の変更を別のスレッドで確定させる（待ち合わせの口をテストで差し替える）。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(TestPasswordChangeBarrier.Config.class)
class PasswordChangeConcurrencyIT {

    private static final String ORIGINAL = "元のパスワード-0000001";

    private static final String FIRST = "先に確定するパスワード-01";

    private static final String LATER = "後の要求のパスワード-0002";

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.60", "IT", null);

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    UserPreferencesService service;

    @Autowired
    TestPasswordChangeBarrier barrier;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName(
            "a change committed after matching makes the later request a mismatch, and only the first new password works")
    void laterRequestLoses() {
        long userId = TestUserAccounts.create(
                userAccountService, "concurrent-" + UUID.randomUUID() + "@example.com", ORIGINAL, false);
        AtomicReference<PasswordChangeResult> first = new AtomicReference<>();
        barrier.runOnce(() ->
                first.set(service.changePassword(userId, PasswordChangeCommand.of(ORIGINAL, FIRST, FIRST), ORIGIN)));

        PasswordChangeResult later =
                service.changePassword(userId, PasswordChangeCommand.of(ORIGINAL, LATER, LATER), ORIGIN);

        assertThat(first.get()).isEqualTo(new PasswordChangeResult.Changed());
        assertThat(later).isEqualTo(new PasswordChangeResult.CurrentMismatch());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
        assertThat(passwordEncoder.matches(FIRST, hash)).isTrue();
        assertThat(passwordEncoder.matches(LATER, hash)).isFalse();
        assertThat(passwordEncoder.matches(ORIGINAL, hash)).isFalse();
        List<String> audits = jdbc.queryForList(
                "SELECT result || ':' || COALESCE(failure_reason, '-') FROM audit_events"
                        + " WHERE event_type = 'PASSWORD_CHANGED' AND target_user_id = ? ORDER BY audit_event_id",
                String.class,
                userId);
        assertThat(audits).containsExactlyInAnyOrder("SUCCESS:-", "FAILURE:CURRENT_PASSWORD_MISMATCH");
    }
}
