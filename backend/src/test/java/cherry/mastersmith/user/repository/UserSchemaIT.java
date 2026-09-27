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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** 利用者の表（V2）の結合テスト（組み込みの H2）。 */
@SpringBootTest
class UserSchemaIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    EntityManager entityManager;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private User persist(String email, Instant createdAt) {
        return tx.execute(status -> {
            User user = new User(
                    email,
                    "$2a$04$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha",
                    false,
                    createdAt,
                    new Preferences(email, Language.JA, Theme.SYSTEM, FontSize.MD));
            entityManager.persist(user);
            return user;
        });
    }

    @Test
    @DisplayName("U2 migrations are applied successfully at startup")
    void migrationsApplied() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"version\", \"success\" FROM \"flyway_schema_history\" WHERE \"version\" IN ('2', '3')");
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row).containsEntry("success", true));
    }

    @Test
    @DisplayName("a user entity is saved and read back with the schema")
    void entityMatchesSchema() {
        String email = uniqueEmail();
        User saved = persist(email, Instant.parse("2026-09-22T00:00:00Z"));

        User found = tx.execute(status -> entityManager.find(User.class, saved.getUserId()));

        assertThat(saved.getUserId()).isPositive();
        assertThat(found.getEmail()).isEqualTo(email);
        assertThat(found.isAdminFlag()).isFalse();
    }

    @Test
    @DisplayName("the same email address cannot be stored twice")
    void emailIsUnique() {
        String email = uniqueEmail();
        persist(email, Instant.now());

        assertThatThrownBy(() -> persist(email, Instant.now())).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("password hash and email are required columns")
    void requiredColumns() {
        // V7 の後は display_name も必須のため、password_hash だけが無い形で確かめる。
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                                + " VALUES (?, NULL, FALSE, ?, 'x')",
                        uniqueEmail(),
                        OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("PASSWORD_HASH");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)"
                                + " VALUES (NULL, 'h', FALSE, ?, 'x')",
                        OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("EMAIL");
    }

    @Test
    @DisplayName("the V7 migration is applied and the entity reads back the name and the three display values")
    void preferencesColumns() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"version\", \"success\" FROM \"flyway_schema_history\" WHERE \"version\" = '7'");
        assertThat(rows).hasSize(1).allSatisfy(row -> assertThat(row).containsEntry("success", true));
        String email = uniqueEmail();
        User saved = tx.execute(status -> {
            User user = new User(
                    email,
                    "$2a$04$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha",
                    false,
                    Instant.parse("2026-09-27T00:00:00Z"),
                    new Preferences("😀".repeat(254), Language.EN, Theme.DARK, FontSize.LG));
            entityManager.persist(user);
            return user;
        });

        User found = tx.execute(status -> entityManager.find(User.class, saved.getUserId()));
        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT display_name, language, theme, font_size FROM users WHERE user_id = ?", saved.getUserId());

        assertThat(found.getPreferences())
                .isEqualTo(new Preferences("😀".repeat(254), Language.EN, Theme.DARK, FontSize.LG));
        assertThat(stored)
                .containsEntry("LANGUAGE", "en")
                .containsEntry("THEME", "dark")
                .containsEntry("FONT_SIZE", "lg");
        assertThat((String) stored.get("DISPLAY_NAME")).hasSize(508);
    }

    @Test
    @DisplayName("display_name is required while language, theme and font_size fall back to ja, system and md")
    void preferencesColumnsRequiredAndDefaults() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO users (email, password_hash, admin_flag, created_at) VALUES (?, 'h', FALSE, ?)",
                        uniqueEmail(),
                        OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        String email = uniqueEmail();
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES (?, 'h', FALSE, ?, ?)",
                email,
                OffsetDateTime.now(),
                email);

        Map<String, Object> row =
                jdbc.queryForMap("SELECT language, theme, font_size FROM users WHERE email = ?", email);

        assertThat(row)
                .containsEntry("LANGUAGE", "ja")
                .containsEntry("THEME", "system")
                .containsEntry("FONT_SIZE", "md");
    }

    @Test
    @DisplayName("an unexpected stored display value fails fast when read")
    void unexpectedStoredValueFailsFast() {
        String email = uniqueEmail();
        jdbc.update(
                "INSERT INTO users (email, password_hash, admin_flag, created_at, display_name, theme)"
                        + " VALUES (?, 'h', FALSE, ?, ?, 'DARK')",
                email,
                OffsetDateTime.now(),
                email);
        Long id = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);

        assertThatThrownBy(() -> tx.execute(status -> entityManager.find(User.class, id)))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("created_at is stored as an instant and does not shift with the JVM time zone")
    void createdAtIsInstant() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        Instant createdAt = Instant.parse("2026-09-22T15:30:00Z");
        User saved = persist(uniqueEmail(), createdAt);

        OffsetDateTime stored = jdbc.queryForObject(
                "SELECT created_at FROM users WHERE user_id = ?", OffsetDateTime.class, saved.getUserId());
        User found = tx.execute(status -> entityManager.find(User.class, saved.getUserId()));

        assertThat(stored.toInstant()).isEqualTo(createdAt);
        assertThat(found.getCreatedAt()).isEqualTo(createdAt);
    }
}
