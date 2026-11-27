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

import cherry.mastersmith.common.testsupport.StatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code savePreferences} の JDBC の生成キーでの 1 文の確かめ（Intent 261114-internal-db-postgresql の U2、NFR1.1・BR1.x。計画の Step 7）。
 * 流れた文の本数と、接続を借りた回数を数え、先に変更したエンティティが更新の前に送られること（R-02 (1)）も確かめる。
 */
@SpringBootTest
@Import(StatementCounter.Config.class)
class SavePreferencesStatementCountIT {

    private static final Preferences CHANGED = new Preferences("山田 花子", Language.EN, Theme.DARK, FontSize.LG);

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

    @Autowired
    StatementCounter counter;

    private long user() {
        String email = "count-" + UUID.randomUUID() + "@example.com";
        return repository
                .save(new User(
                        email,
                        "$2a$04$hash",
                        false,
                        Instant.parse("2026-09-22T00:00:00Z"),
                        new Preferences("数え 太郎", Language.JA, Theme.SYSTEM, FontSize.MD)))
                .getUserId();
    }

    private String zoneColumn(long id) {
        return jdbc.queryForObject("SELECT display_time_zone FROM users WHERE user_id = ?", String.class, id);
    }

    @Test
    @DisplayName("one save runs a single statement and borrows one connection for the whole transaction")
    void oneStatementOneConnection() {
        long id = user();

        counter.reset();
        Optional<UserRepository.SavedPreferences> saved =
                tx.execute(status -> repository.savePreferences(id, CHANGED, true, "Asia/Tokyo"));

        assertThat(counter.statements()).as("statements").isEqualTo(1);
        assertThat(counter.connections()).as("connections").isEqualTo(1);
        assertThat(saved).hasValueSatisfying(value -> {
            assertThat(value.getUserId()).isEqualTo(id);
            assertThat(value.getDisplayTimeZone()).isEqualTo("Asia/Tokyo");
        });
    }

    @Test
    @DisplayName("an unknown user returns empty with a single statement")
    void unknownUserReturnsEmpty() {
        counter.reset();
        Optional<UserRepository.SavedPreferences> saved =
                tx.execute(status -> repository.savePreferences(Long.MAX_VALUE, CHANGED, true, "Asia/Tokyo"));

        assertThat(saved).isEmpty();
        assertThat(counter.statements()).isEqualTo(1);
    }

    @Test
    @DisplayName("the zone stays as it is when the change flag is false")
    void zoneKeptWhenNotChanging() {
        long id = user();
        tx.execute(status -> repository.savePreferences(id, CHANGED, true, "Asia/Tokyo"));

        Optional<UserRepository.SavedPreferences> saved =
                tx.execute(status -> repository.savePreferences(id, CHANGED, false, "UTC"));

        assertThat(saved)
                .hasValueSatisfying(
                        value -> assertThat(value.getDisplayTimeZone()).isEqualTo("Asia/Tokyo"));
        assertThat(zoneColumn(id)).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("a null zone with the change flag true clears the column and returns null")
    void nullZoneClearsTheColumn() {
        long id = user();
        tx.execute(status -> repository.savePreferences(id, CHANGED, true, "Asia/Tokyo"));

        Optional<UserRepository.SavedPreferences> saved =
                tx.execute(status -> repository.savePreferences(id, CHANGED, true, null));

        assertThat(saved)
                .hasValueSatisfying(
                        value -> assertThat(value.getDisplayTimeZone()).isNull());
        assertThat(zoneColumn(id)).isNull();
    }

    @Test
    @DisplayName("an entity changed earlier in the transaction is sent before the update and does not overwrite it")
    void pendingEntityChangeIsFlushedFirst() {
        long id = user();

        tx.execute(status -> {
            User managed = repository.findById(id).orElseThrow();
            // 管理された利用者を変更したまま（まだ送られていない）、保存の 1 文を流す。
            ReflectionTestUtils.setField(managed, "suspended", true);
            repository.savePreferences(id, CHANGED, true, "Asia/Tokyo");
            return null;
        });

        assertThat(jdbc.queryForObject("SELECT suspended FROM users WHERE user_id = ?", Boolean.class, id))
                .as("先の変更が送られた")
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE user_id = ?", String.class, id))
                .as("確定の時の送りで氏名が元の値に戻されない")
                .isEqualTo("山田 花子");
        assertThat(zoneColumn(id)).isEqualTo("Asia/Tokyo");
    }
}
