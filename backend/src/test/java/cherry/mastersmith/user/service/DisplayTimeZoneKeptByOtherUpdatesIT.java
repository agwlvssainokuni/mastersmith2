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
import cherry.mastersmith.user.domain.DisplayTimeZoneInput;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 表示のタイムゾーンの列は、保存の 1 文のほかの更新（パスワードの変更・停止と再開・管理者の印・管理者による氏名と言語の変更）で消えない
 * こと、同じ利用者の 2 つの保存が重なっても保存後の 5 項目が片方の要求の値でそろうことの結合テスト（Intent 261102-timezone-ui-polish
 * の U2、BR2.3・BR2.9・BR2.10、NFR4.1.5）。組み込みの H2。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
class DisplayTimeZoneKeptByOtherUpdatesIT {

    private static final String ORIGINAL = "元のパスワード-0000001";

    private static final String CHANGED = "変えたパスワード-0000002";

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
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    private long newUserWithZone(String zone) {
        long userId = TestUserAccounts.create(
                userAccountService, "zone-keep-" + UUID.randomUUID() + "@example.com", ORIGINAL, false);
        service.savePreferences(
                userId, new PreferencesCommand("保存 者", "ja", "light", "md", new DisplayTimeZoneInput.Text(zone)));
        return userId;
    }

    private String zone(long userId) {
        return jdbc.queryForObject("SELECT display_time_zone FROM users WHERE user_id = ?", String.class, userId);
    }

    @Test
    @DisplayName("a password change leaves the saved zone")
    void passwordChange() {
        long userId = newUserWithZone("Asia/Tokyo");

        PasswordChangeResult result = service.changePassword(
                userId,
                PasswordChangeCommand.of(ORIGINAL, CHANGED, CHANGED),
                new RequestOrigin("192.0.2.70", null, null));

        assertThat(result).isEqualTo(new PasswordChangeResult.Changed());
        assertThat(zone(userId)).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("suspending, resuming and setting or removing the admin mark leave the saved zone")
    void suspensionAndAdminMark() {
        long userId = newUserWithZone("Europe/London");

        tx.executeWithoutResult(status -> userAccountService.setSuspended(userId, true));
        tx.executeWithoutResult(status -> userAccountService.setSuspended(userId, false));
        tx.executeWithoutResult(status -> userAccountService.setAdmin(userId, true));
        tx.executeWithoutResult(status -> userAccountService.setAdmin(userId, false));

        assertThat(zone(userId)).isEqualTo("Europe/London");
    }

    @Test
    @DisplayName("an administrator changing the name and the language leaves the saved zone")
    void profileUpdateByAdmin() {
        long userId = newUserWithZone("Asia/Kolkata");

        tx.executeWithoutResult(
                status -> userAccountService.updateProfile(userId, new ProfileCommand("管理者が変えた 氏名", "en")));

        assertThat(zone(userId)).isEqualTo("Asia/Kolkata");
        assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE user_id = ?", String.class, userId))
                .isEqualTo("管理者が変えた 氏名");
    }

    @Test
    @DisplayName("two overlapping saves leave the five values of one request, never a mix")
    void overlappingSavesLeaveOneRequestsValues() throws Exception {
        long userId = newUserWithZone("Asia/Tokyo");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                CyclicBarrier start = new CyclicBarrier(2);
                List<Future<PreferencesResult>> futures = new ArrayList<>();
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return service.savePreferences(
                            userId,
                            new PreferencesCommand(
                                    "一番 目", "ja", "light", "sm", new DisplayTimeZoneInput.Text("Asia/Tokyo")));
                }));
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return service.savePreferences(
                            userId,
                            new PreferencesCommand(
                                    "二番 目", "en", "dark", "lg", new DisplayTimeZoneInput.Text("America/New_York")));
                }));
                for (Future<PreferencesResult> future : futures) {
                    assertThat(future.get(30, TimeUnit.SECONDS)).isInstanceOf(PreferencesResult.Ok.class);
                }

                var row = jdbc.queryForMap(
                        "SELECT display_name, language, theme, font_size, display_time_zone FROM users WHERE user_id = ?",
                        userId);
                boolean first = "一番 目".equals(row.get("DISPLAY_NAME"));
                assertThat(row.get("LANGUAGE")).isEqualTo(first ? "ja" : "en");
                assertThat(row.get("THEME")).isEqualTo(first ? "light" : "dark");
                assertThat(row.get("FONT_SIZE")).isEqualTo(first ? "sm" : "lg");
                assertThat(row.get("DISPLAY_TIME_ZONE")).isEqualTo(first ? "Asia/Tokyo" : "America/New_York");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
