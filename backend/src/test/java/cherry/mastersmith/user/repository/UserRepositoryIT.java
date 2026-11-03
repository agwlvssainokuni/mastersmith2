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
import java.util.Optional;
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
                "SELECT email, password_hash, admin_flag, created_at, display_name, language, theme, font_size, suspended"
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

    /** 表示のタイムゾーンを書かない保存（前の版の画面と同じ 4 項目）。 */
    private int keepZone(long id, Preferences preferences) {
        return tx.execute(status -> repository.savePreferences(id, preferences, false, null))
                .map(saved -> 1)
                .orElse(0);
    }

    private Optional<UserRepository.SavedPreferences> savePrefs(
            long id, Preferences preferences, boolean change, String zone) {
        return tx.execute(status -> repository.savePreferences(id, preferences, change, zone));
    }

    private String zoneColumn(long id) {
        return jdbc.queryForObject("SELECT display_time_zone FROM users WHERE user_id = ?", String.class, id);
    }

    @Test
    @DisplayName("saving the preferences without changing the zone rewrites only the four columns")
    void updatePreferencesTouchesOnlyFourColumns() {
        String email = "prefs-" + UUID.randomUUID() + "@example.com";
        long id = save(email, true).getUserId();
        Map<String, Object> before = row(id);

        int rows = keepZone(id, CHANGED);

        Map<String, Object> after = row(id);
        assertThat(rows).isEqualTo(1);
        assertThat(after)
                .containsEntry("DISPLAY_NAME", "山田 花子")
                .containsEntry("LANGUAGE", "en")
                .containsEntry("THEME", "dark")
                .containsEntry("FONT_SIZE", "lg");
        for (String column : new String[] {"EMAIL", "PASSWORD_HASH", "ADMIN_FLAG", "SUSPENDED"}) {
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
        keepZone(id, CHANGED);

        tx.execute(status ->
                repository.updatePasswordHashIfUnchanged(id, new PasswordHash(HASH), new PasswordHash("$2a$04$new3")));

        assertThat(row(id)).containsEntry("DISPLAY_NAME", "山田 花子").containsEntry("THEME", "dark");
    }

    @Test
    @DisplayName("both updates of an unknown user change nothing and return zero")
    void unknownUser() {
        Optional<UserRepository.SavedPreferences> preferences = savePrefs(Long.MAX_VALUE, CHANGED, true, "Asia/Tokyo");
        Integer hash = tx.execute(status -> repository.updatePasswordHashIfUnchanged(
                Long.MAX_VALUE, new PasswordHash(HASH), new PasswordHash("$2a$04$x")));

        assertThat(preferences).isEmpty();
        assertThat(hash).isZero();
    }

    @Test
    @DisplayName("reading in the same transaction after an update sees the new values because the context is cleared")
    void readAfterUpdateSeesNewValues() {
        long id = save("ctx-" + UUID.randomUUID() + "@example.com", false).getUserId();

        // 保存の 1 文は結果を返す文のため持続化の文脈を空にしない。業務処理は保存の後に読み直さず、次のトランザクションで読む。
        tx.execute(status -> {
            repository.findById(id).orElseThrow();
            repository.savePreferences(id, CHANGED, true, "Asia/Tokyo");
            return null;
        });
        Preferences read =
                tx.execute(status -> repository.findById(id).orElseThrow().getPreferences());
        String hash = tx.execute(status -> {
            repository.findById(id).orElseThrow();
            repository.updatePasswordHashIfUnchanged(id, new PasswordHash(HASH), new PasswordHash("$2a$04$new4"));
            return repository.findById(id).orElseThrow().getPasswordHash();
        });

        assertThat(read).isEqualTo(CHANGED);
        assertThat(hash).isEqualTo("$2a$04$new4");
    }

    @Test
    @DisplayName("updating the suspension rewrites only the suspended column")
    void updateSuspendedTouchesOnlyTheSuspendedColumn() {
        String email = "suspend-" + UUID.randomUUID() + "@example.com";
        long id = save(email, true).getUserId();
        keepZone(id, CHANGED);
        Map<String, Object> before = row(id);

        Integer suspended = tx.execute(status -> repository.updateSuspended(id, true));
        Map<String, Object> afterSuspend = row(id);
        Integer resumed = tx.execute(status -> repository.updateSuspended(id, false));
        Map<String, Object> afterResume = row(id);

        assertThat(suspended).isEqualTo(1);
        assertThat(resumed).isEqualTo(1);
        assertThat(before).containsEntry("SUSPENDED", false);
        assertThat(afterSuspend).containsEntry("SUSPENDED", true);
        assertThat(afterResume).containsEntry("SUSPENDED", false);
        for (String column :
                new String[] {"EMAIL", "PASSWORD_HASH", "ADMIN_FLAG", "DISPLAY_NAME", "LANGUAGE", "THEME", "FONT_SIZE"
                }) {
            assertThat(afterSuspend.get(column)).as(column).isEqualTo(before.get(column));
            assertThat(afterResume.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(((OffsetDateTime) afterSuspend.get("CREATED_AT")).toInstant())
                .isEqualTo(((OffsetDateTime) before.get("CREATED_AT")).toInstant());
    }

    @Test
    @DisplayName("updating the suspension of an unknown user changes nothing and returns zero")
    void updateSuspendedOfUnknownUser() {
        long other = save("other-" + UUID.randomUUID() + "@example.com", false).getUserId();

        Integer rows = tx.execute(status -> repository.updateSuspended(Long.MAX_VALUE, true));

        assertThat(rows).isZero();
        assertThat(row(other)).containsEntry("SUSPENDED", false);
    }

    @Test
    @DisplayName("reading by id after the suspension update in the same transaction sees the written value")
    void readAfterUpdateSuspendedSeesNewValue() {
        long id =
                save("suspend-ctx-" + UUID.randomUUID() + "@example.com", false).getUserId();

        boolean[] read = tx.execute(status -> {
            User loaded = repository.findById(id).orElseThrow();
            boolean beforeUpdate = loaded.isSuspended();
            repository.updateSuspended(id, true);
            boolean afterSuspend = repository.findById(id).orElseThrow().isSuspended();
            repository.updateSuspended(id, false);
            boolean afterResume = repository.findById(id).orElseThrow().isSuspended();
            return new boolean[] {beforeUpdate, afterSuspend, afterResume};
        });

        assertThat(read).containsExactly(false, true, false);
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

    @Test
    @DisplayName("Keep leaves the zone column and returns the stored value")
    void keepLeavesTheZone() {
        long id = save("keep-zone-" + UUID.randomUUID() + "@example.com", false).getUserId();
        savePrefs(id, CHANGED, true, "Asia/Kolkata");

        Optional<UserRepository.SavedPreferences> saved = savePrefs(id, CHANGED, false, "America/New_York");

        assertThat(saved).hasValueSatisfying(value -> {
            assertThat(value.getUserId()).isEqualTo(id);
            assertThat(value.getDisplayTimeZone()).isEqualTo("Asia/Kolkata");
        });
        assertThat(zoneColumn(id)).isEqualTo("Asia/Kolkata");
    }

    @Test
    @DisplayName("Clear writes NULL and returns null, Set writes the name and returns it")
    void clearAndSet() {
        long id = save("set-zone-" + UUID.randomUUID() + "@example.com", false).getUserId();

        Optional<UserRepository.SavedPreferences> set = savePrefs(id, CHANGED, true, "Asia/Tokyo");
        assertThat(set)
                .hasValueSatisfying(
                        value -> assertThat(value.getDisplayTimeZone()).isEqualTo("Asia/Tokyo"));
        assertThat(zoneColumn(id)).isEqualTo("Asia/Tokyo");

        Optional<UserRepository.SavedPreferences> cleared = savePrefs(id, CHANGED, true, null);
        assertThat(cleared)
                .hasValueSatisfying(
                        value -> assertThat(value.getDisplayTimeZone()).isNull());
        assertThat(zoneColumn(id)).isNull();
    }

    @Test
    @DisplayName("the five values are committed by the one statement and other columns stay")
    void fiveValuesInOneStatement() {
        long id = save("five-" + UUID.randomUUID() + "@example.com", true).getUserId();
        Map<String, Object> before = row(id);

        savePrefs(id, CHANGED, true, "Europe/London");

        Map<String, Object> after = row(id);
        assertThat(after)
                .containsEntry("DISPLAY_NAME", "山田 花子")
                .containsEntry("LANGUAGE", "en")
                .containsEntry("THEME", "dark")
                .containsEntry("FONT_SIZE", "lg");
        assertThat(zoneColumn(id)).isEqualTo("Europe/London");
        for (String column : new String[] {"EMAIL", "PASSWORD_HASH", "ADMIN_FLAG", "SUSPENDED"}) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(((OffsetDateTime) after.get("CREATED_AT")).toInstant())
                .isEqualTo(((OffsetDateTime) before.get("CREATED_AT")).toInstant());
    }

    @Test
    @DisplayName("a name with quotes, semicolons, comment marks and newlines is bound as a value and stored as is")
    void specialCharactersAreBound() {
        long id = save("special-" + UUID.randomUUID() + "@example.com", false).getUserId();
        Preferences special =
                new Preferences("o'neil \"x\"; -- DROP TABLE users", Language.JA, Theme.LIGHT, FontSize.SM);

        savePrefs(id, special, true, "Asia/Tokyo");

        assertThat(row(id)).containsEntry("DISPLAY_NAME", "o'neil \"x\"; -- DROP TABLE users");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "an entity read after the save in the same transaction can still be read, and the column is not updatable by the entity")
    void entityReadableAfterSave() {
        long id = save("entity-" + UUID.randomUUID() + "@example.com", false).getUserId();

        String zone = tx.execute(status -> {
            repository.findById(id).orElseThrow();
            repository.savePreferences(id, CHANGED, true, "Asia/Tokyo");
            return zoneColumn(id);
        });
        String entityZone =
                tx.execute(status -> repository.findById(id).orElseThrow().getDisplayTimeZone());

        assertThat(zone).isEqualTo("Asia/Tokyo");
        assertThat(entityZone).isEqualTo("Asia/Tokyo");
    }
}
