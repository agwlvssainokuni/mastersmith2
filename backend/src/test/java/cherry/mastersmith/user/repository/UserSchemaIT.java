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
            User user =
                    new User(email, "$2a$04$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha", false, createdAt);
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
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO users (email, password_hash, admin_flag, created_at) VALUES (?, NULL, FALSE, ?)",
                        uniqueEmail(),
                        OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
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
