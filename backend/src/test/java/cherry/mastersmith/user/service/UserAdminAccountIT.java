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

import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 利用者の管理に向けた UserAccount の口（U3、契約 C8 の findAdminPage・updateProfile、BR1.6・BR5.2）の結合テスト。 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class UserAdminAccountIT {

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

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    TransactionTemplate tx;

    private User save(String email, String name) {
        return repository.save(new User(
                email,
                "$2a$04$hash",
                false,
                Instant.parse("2026-09-22T00:00:00Z"),
                new Preferences(name, Language.JA, Theme.SYSTEM, FontSize.MD)));
    }

    @Test
    @DisplayName("one read-only transaction reads the count and the rows of the same search")
    void countAndRowsInOneRead() {
        String mark = "m" + UUID.randomUUID().toString().replace("-", "");
        save("a-" + mark + "@example.com", "一覧 A");
        save("b-" + mark + "@example.com", "一覧 B");
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        SqlStatementCounter.start();
        UserAdminSlice slice = readOnly.execute(status -> service.findAdminPage(new SearchText(mark), 0, 20));
        Map<String, List<String>> statements = SqlStatementCounter.stop();

        assertThat(slice.total()).isEqualTo(2);
        assertThat(slice.items()).extracting(UserAdminSummary::displayName).containsExactly("一覧 A", "一覧 B");
        assertThat(statements).hasSize(1);
        assertThat(statements.values().iterator().next()).containsExactly("select users", "select users");
    }

    @Test
    @DisplayName("updating a profile outside a caller's transaction is refused")
    void updateProfileNeedsTransaction() {
        User user = save("mandatory-" + UUID.randomUUID() + "@example.com", "元の氏名");

        assertThatThrownBy(() -> service.updateProfile(user.getUserId(), new ProfileCommand("新しい氏名", "en")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT display_name FROM users WHERE user_id = ?", String.class, user.getUserId()))
                .isEqualTo("元の氏名");
    }

    @Test
    @DisplayName("inside a transaction a valid profile is written and an invalid one issues no SQL")
    void updateProfileInTransaction() {
        User user = save("profile-" + UUID.randomUUID() + "@example.com", "元の氏名");

        ProfileUpdateResult updated =
                tx.execute(status -> service.updateProfile(user.getUserId(), new ProfileCommand(" 新しい氏名 ", "en")));
        SqlStatementCounter.start();
        ProfileUpdateResult invalid =
                tx.execute(status -> service.updateProfile(user.getUserId(), new ProfileCommand("", "JA")));
        Map<String, List<String>> statements = SqlStatementCounter.stop();

        assertThat(updated).isEqualTo(new ProfileUpdateResult.Updated());
        assertThat(invalid).isInstanceOf(ProfileUpdateResult.Invalid.class);
        assertThat(statements).isEmpty();
        assertThat(jdbc.queryForMap("SELECT display_name, language FROM users WHERE user_id = ?", user.getUserId()))
                .containsEntry("DISPLAY_NAME", "新しい氏名")
                .containsEntry("LANGUAGE", "en");
    }
}
