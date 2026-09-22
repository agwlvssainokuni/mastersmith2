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
import cherry.mastersmith.user.domain.User;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

    private User save(String email, boolean admin) {
        return repository.save(new User(email, "$2a$04$hash", admin, Instant.parse("2026-09-22T00:00:00Z")));
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
}
