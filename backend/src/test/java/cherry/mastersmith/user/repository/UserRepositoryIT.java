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
package cherry.mastersmith.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.PasswordHash;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.RedactedText;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import java.nio.file.Path;
import java.time.Instant;
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
import org.springframework.transaction.support.TransactionTemplate;

/** 利用者の DB アクセスの結合テスト。 */
@SpringBootTest
class UserRepositoryIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    private static final String HASH = "$2a$04$hash";

    private static final Preferences CHANGED = new Preferences("山田 花子", Language.EN, Theme.DARK, FontSize.LG);

    private Map<String, Object> row(long userId) {
        return jdbc.queryForMap(
                "SELECT email, password_hash, admin_flag, created_at, display_name, language, theme, font_size"
                        + " FROM users WHERE user_id = ?",
                userId);
    }

    private static Preferences preferences(String email) {
        return new Preferences(email, Language.JA, Theme.SYSTEM, FontSize.MD);
    }

    private User save(String email, boolean admin) {
        return repository.save(
                new User(email, "$2a$04$hash", admin, Instant.parse("2026-09-22T00:00:00Z"), preferences(email)));
    }

    @Test
    @DisplayName("a user is found by the lower-cased email address")
    void findByEmail() {
        String email = "管理者-" + UUID.randomUUID() + "@example.com";
        User saved = save(email, true);

        assertThat(repository.findByEmail(email)).hasValueSatisfying(found -> {
            assertThat(found.getUserId()).isEqualTo(saved.getUserId());
            assertThat(found.isAdminFlag()).isTrue();
        });
    }

    @Test
    @DisplayName("an unknown email address returns empty")
    void unknownEmail() {
        assertThat(repository.findByEmail("nobody-" + UUID.randomUUID() + "@example.com"))
                .isEmpty();
    }

    @Test
    @DisplayName("the lookup does not ignore case by itself (callers pass the normalized value)")
    void caseSensitiveLookup() {
        String email = "case-" + UUID.randomUUID() + "@example.com";
        save(email, false);

        assertThat(repository.findByEmail(email.toUpperCase(java.util.Locale.ROOT)))
                .isEmpty();
    }

    @Test
    @DisplayName("a user is found by id")
    void findById() {
        User saved = save("id-" + UUID.randomUUID() + "@example.com", false);

        assertThat(repository.findById(saved.getUserId())).isPresent();
        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("updating the preferences rewrites only the four columns")
    void updatePreferencesTouchesOnlyFourColumns() {
        String email = "prefs-" + UUID.randomUUID() + "@example.com";
        long id = save(email, true).getUserId();
        Map<String, Object> before = row(id);

        Integer rows = tx.execute(status -> repository.updatePreferences(id, CHANGED));

        Map<String, Object> after = row(id);
        assertThat(rows).isEqualTo(1);
        assertThat(after)
                .containsEntry("DISPLAY_NAME", "山田 花子")
                .containsEntry("LANGUAGE", "en")
                .containsEntry("THEME", "dark")
                .containsEntry("FONT_SIZE", "lg");
        for (String column : new String[] {"EMAIL", "PASSWORD_HASH", "ADMIN_FLAG"}) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(((OffsetDateTime) after.get("CREATED_AT")).toInstant())
                .isEqualTo(((OffsetDateTime) before.get("CREATED_AT")).toInstant());
    }

    @Test
    @DisplayName("the conditional hash update writes when the read hash is current and writes nothing otherwise")
    void conditionalHashUpdate() {
        long id = save("hash-" + UUID.randomUUID() + "@example.com", false).getUserId();

        Integer stale = tx.execute(status -> repository.updatePasswordHashIfUnchanged(
                id, new PasswordHash("$2a$04$other"), new PasswordHash("$2a$04$new1")));
        assertThat(stale).isZero();
        assertThat(row(id)).containsEntry("PASSWORD_HASH", HASH);

        Integer current = tx.execute(status ->
                repository.updatePasswordHashIfUnchanged(id, new PasswordHash(HASH), new PasswordHash("$2a$04$new2")));
        assertThat(current).isEqualTo(1);
        assertThat(row(id)).containsEntry("PASSWORD_HASH", "$2a$04$new2");
    }

    @Test
    @DisplayName("changing the password leaves the four preference columns as they are")
    void hashUpdateKeepsPreferences() {
        String email = "keep-" + UUID.randomUUID() + "@example.com";
        long id = save(email, false).getUserId();
        tx.execute(status -> repository.updatePreferences(id, CHANGED));

        tx.execute(status ->
                repository.updatePasswordHashIfUnchanged(id, new PasswordHash(HASH), new PasswordHash("$2a$04$new3")));

        assertThat(row(id)).containsEntry("DISPLAY_NAME", "山田 花子").containsEntry("THEME", "dark");
    }

    @Test
    @DisplayName("both updates of an unknown user change nothing and return zero")
    void unknownUser() {
        Integer preferences = tx.execute(status -> repository.updatePreferences(Long.MAX_VALUE, CHANGED));
        Integer hash = tx.execute(status -> repository.updatePasswordHashIfUnchanged(
                Long.MAX_VALUE, new PasswordHash(HASH), new PasswordHash("$2a$04$x")));

        assertThat(preferences).isZero();
        assertThat(hash).isZero();
    }

    @Test
    @DisplayName("reading in the same transaction after an update sees the new values because the context is cleared")
    void readAfterUpdateSeesNewValues() {
        long id = save("ctx-" + UUID.randomUUID() + "@example.com", false).getUserId();

        Preferences read = tx.execute(status -> {
            repository.findById(id).orElseThrow();
            repository.updatePreferences(id, CHANGED);
            return repository.findById(id).orElseThrow().getPreferences();
        });
        String hash = tx.execute(status -> {
            repository.findById(id).orElseThrow();
            repository.updatePasswordHashIfUnchanged(id, new PasswordHash(HASH), new PasswordHash("$2a$04$new4"));
            return repository.findById(id).orElseThrow().getPasswordHash();
        });

        assertThat(read).isEqualTo(CHANGED);
        assertThat(hash).isEqualTo("$2a$04$new4");
    }

    @Test
    @DisplayName("the redacted lookup tells whether the normalized email exists (U3 decision 3)")
    void existsByRedactedEmail() {
        String email = "redacted-" + UUID.randomUUID() + "@example.com";
        save(email, false);

        assertThat(repository.existsByRedactedEmail(new RedactedText(email))).isTrue();
        assertThat(repository.existsByRedactedEmail(new RedactedText("nobody-" + UUID.randomUUID() + "@example.com")))
                .isFalse();
    }

    @Test
    @DisplayName("the redacted lookup is case sensitive like findByEmail (callers pass the normalized value)")
    void existsByRedactedEmailIsCaseSensitive() {
        String email = "redacted-case-" + UUID.randomUUID() + "@example.com";
        save(email, false);

        assertThat(repository.existsByRedactedEmail(new RedactedText(email.toUpperCase(java.util.Locale.ROOT))))
                .isFalse();
    }
}
