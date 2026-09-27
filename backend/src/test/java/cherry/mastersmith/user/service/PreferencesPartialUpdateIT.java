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
import java.util.Map;
import java.util.UUID;
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
 * 部分の書き換え（BR3.2〜BR3.4、NFR9.8、{@code reliability-design.md} 3節）の結合テスト（組み込みの H2）。
 *
 * <p>プリファレンスの保存とパスワードの変更の重なりは、パスワードの変更の照合の後・書き込みの前に、保存を別のスレッドで確定させて作る。
 */
@SpringBootTest(properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(TestPasswordChangeBarrier.Config.class)
class PreferencesPartialUpdateIT {

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
    TestPasswordChangeBarrier barrier;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    private long newUser() {
        return TestUserAccounts.create(
                userAccountService, "partial-" + UUID.randomUUID() + "@example.com", ORIGINAL, false);
    }

    private Map<String, Object> row(long userId) {
        return jdbc.queryForMap(
                "SELECT display_name, language, theme, font_size, password_hash FROM users WHERE user_id = ?", userId);
    }

    @Test
    @DisplayName("a preference save committed in the middle of a password change keeps both results")
    void overlappingSaveAndPasswordChangeKeepBoth() {
        long userId = newUser();
        barrier.runOnce(() -> service.savePreferences(userId, new PreferencesCommand("重なった 保存", "en", "dark", "lg")));

        PasswordChangeResult result = service.changePassword(
                userId,
                PasswordChangeCommand.of(ORIGINAL, CHANGED, CHANGED),
                new RequestOrigin("192.0.2.61", null, null));

        assertThat(result).isEqualTo(new PasswordChangeResult.Changed());
        Map<String, Object> row = row(userId);
        assertThat(row)
                .containsEntry("DISPLAY_NAME", "重なった 保存")
                .containsEntry("LANGUAGE", "en")
                .containsEntry("THEME", "dark")
                .containsEntry("FONT_SIZE", "lg");
        assertThat(passwordEncoder.matches(CHANGED, (String) row.get("PASSWORD_HASH")))
                .isTrue();
    }

    @Test
    @DisplayName("one invalid field leaves all four columns unchanged")
    void oneErrorChangesNothing() {
        long userId = newUser();
        Map<String, Object> before = row(userId);

        PreferencesResult result =
                service.savePreferences(userId, new PreferencesCommand("新しい 名前", "en", "dark", "xl"));

        assertThat(result).isInstanceOf(PreferencesResult.Invalid.class);
        assertThat(row(userId)).isEqualTo(before);
    }

    @Test
    @DisplayName("of two saves of the same columns, the one committed later remains")
    void laterSaveWins() {
        long userId = newUser();

        service.savePreferences(userId, new PreferencesCommand("先の 保存", "ja", "light", "sm"));
        service.savePreferences(userId, new PreferencesCommand("後の 保存", "en", "dark", "lg"));

        assertThat(row(userId))
                .containsEntry("DISPLAY_NAME", "後の 保存")
                .containsEntry("LANGUAGE", "en")
                .containsEntry("THEME", "dark")
                .containsEntry("FONT_SIZE", "lg");
        assertThat(service.getPreferences(userId))
                .isInstanceOfSatisfying(
                        PreferencesResult.Ok.class,
                        ok -> assertThat(ok.preferences().displayName()).isEqualTo("後の 保存"));
    }
}
