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

import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 利用者の要約を ID の集合でまとめて読む口（Intent 261004-role-menu の U3、{@code findSummariesByIds}、BR9.1・NFR1.6、
 * {@code performance-design.md} 1節）の結合テスト。
 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class UserSummariesByIdsIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    UserAccountService service;

    @Autowired
    UserRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private User save(String name, boolean suspended) {
        User user = repository.save(new User(
                "member-" + UUID.randomUUID() + "@example.com",
                "$2a$04$hash",
                false,
                Instant.parse("2026-10-01T00:00:00Z"),
                new Preferences(name, Language.JA, Theme.SYSTEM, FontSize.MD)));
        if (suspended) {
            jdbc.update("UPDATE users SET suspended = TRUE WHERE user_id = ?", user.getUserId());
        }
        return user;
    }

    @Test
    @DisplayName("the summaries of many users are read with one statement in user id order")
    void readsManyWithOneStatement() {
        User first = save("営業 太郎", false);
        User second = save("営業 花子", true);
        User third = save("営業 次郎", false);

        SqlStatementCounter.start();
        List<UserAdminSummary> summaries =
                service.findSummariesByIds(Set.of(third.getUserId(), first.getUserId(), second.getUserId()));
        Map<String, List<String>> statements = SqlStatementCounter.stop();

        assertThat(summaries)
                .extracting(UserAdminSummary::userId)
                .containsExactly(first.getUserId(), second.getUserId(), third.getUserId());
        assertThat(summaries)
                .extracting(UserAdminSummary::displayName, UserAdminSummary::suspended)
                .containsExactly(Tuple.tuple("営業 太郎", false), Tuple.tuple("営業 花子", true), Tuple.tuple("営業 次郎", false));
        assertThat(statements.values()).singleElement().isEqualTo(List.of("select users"));
    }

    @Test
    @DisplayName("ids without a user are left out and an empty set issues no statement")
    void missingIdsAndEmptySet() {
        User user = save("営業 三郎", false);

        List<UserAdminSummary> found = service.findSummariesByIds(Set.of(user.getUserId(), 0L, -1L, Long.MAX_VALUE));
        SqlStatementCounter.start();
        List<UserAdminSummary> empty = service.findSummariesByIds(Set.of());
        Map<String, List<String>> statements = SqlStatementCounter.stop();

        assertThat(found).extracting(UserAdminSummary::userId).containsExactly(user.getUserId());
        assertThat(empty).isEmpty();
        assertThat(statements).isEmpty();
    }

    @Test
    @DisplayName("the string form of a summary hides the email address and the display name")
    void stringFormIsRedacted() {
        User user = save("営業 四郎", false);

        UserAdminSummary summary =
                service.findSummariesByIds(Set.of(user.getUserId())).getFirst();

        assertThat(summary.email()).isEqualTo(user.getEmail());
        assertThat(summary.toString())
                .contains("userId=" + user.getUserId())
                .doesNotContain(user.getEmail())
                .doesNotContain("営業 四郎")
                .doesNotContain("$2a$04$hash");
    }
}
