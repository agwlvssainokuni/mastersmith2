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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.StatementCounter;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.PgApp;
import cherry.mastersmith.internaldb.testsupport.PortableFixture;
import cherry.mastersmith.role.store.Referent;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 書き直した SQL の結果と文の数（BR5.1）。{@code savePreferences}・作業ロールの保存・プレビューを置くが、PostgreSQL でも H2 の参照のテスト
 * （{@code SavePreferencesStatementCountIT}・{@code WorkRoleSelectionStatementCountIT}・{@code DslPreviewStatementCountIT}）と同じ結果と文の数・接続の数になる。
 * 文の数は、アプリに足した {@code StatementCounter}（U2 の部品。読むだけで使う）で数える。
 */
@InternalDbPostgresIT
class PgPortableSqlIT {

    private static final Preferences CHANGED = new Preferences("山田 花子", Language.EN, Theme.DARK, FontSize.LG);

    private static final Instant NOW = Instant.parse("2026-10-08T07:00:00Z");

    private static PgApp app;

    private static PortableFixture fixture;

    private static StatementCounter counter;

    private static TransactionTemplate tx;

    @BeforeAll
    static void start() {
        app = PgApp.startWithSources(new Class<?>[] {StatementCounter.Config.class});
        fixture = new PortableFixture(app);
        counter = app.context().getBean(StatementCounter.class);
        tx = new TransactionTemplate(app.context().getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private static RoleStoreOutcome<Void> saveWorkRole(long userId, long roleId, Instant at) {
        RoleStore store = app.context().getBean(RoleStore.class);
        return tx.execute(status -> {
            RoleStoreOutcome<Void> outcome = store.saveWorkRoleSelection(userId, roleId, at);
            if (!outcome.isDone()) {
                status.setRollbackOnly();
            }
            return outcome;
        });
    }

    @Test
    @DisplayName(
            "the first work role save runs an update and an insert, later saves run a single update, all on one connection")
    void workRoleSelectionStatementCounts() {
        PortableFixture.Account member = fixture.createUser(false);
        long first = fixture.createRole(PortableFixture.uniqueName("sql"));
        long second = fixture.createRole(PortableFixture.uniqueName("sql"));

        counter.reset();
        assertThat(saveWorkRole(member.userId(), first, NOW)).isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(counter.statements()).as("first save statements").isEqualTo(2);
        assertThat(counter.connections()).as("first save connections").isEqualTo(1);

        counter.reset();
        assertThat(saveWorkRole(member.userId(), second, NOW.plusSeconds(5)))
                .isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(counter.statements()).as("later save statements").isEqualTo(1);
        assertThat(counter.connections()).as("later save connections").isEqualTo(1);

        counter.reset();
        assertThat(saveWorkRole(member.userId(), second, NOW.plusSeconds(9)))
                .isEqualTo(new RoleStoreOutcome.Done<>(null));
        assertThat(counter.statements()).as("same value statements").isEqualTo(1);
        assertThat(counter.connections()).as("same value connections").isEqualTo(1);
        assertThat(fixture.queryLong("SELECT role_id FROM work_role_selections WHERE user_id = " + member.userId()))
                .isEqualTo(second);
    }

    @Test
    @DisplayName("a work role save for a missing user is Referenced to the user and writes no row")
    void workRoleSelectionForeignKey() {
        long roleId = fixture.createRole(PortableFixture.uniqueName("sql"));

        assertThat(saveWorkRole(Long.MAX_VALUE, roleId, NOW))
                .isEqualTo(new RoleStoreOutcome.Referenced<>(Referent.USER));
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM work_role_selections WHERE user_id = " + Long.MAX_VALUE))
                .isZero();
    }

    @Test
    @DisplayName("the first preview place runs an update and an insert, a later one a single update, on one connection")
    void previewPlaceStatementCounts() {
        DslPreviewRepository previews = app.context().getBean(DslPreviewRepository.class);
        long adminId = fixture.adminUserId();
        fixture.executeSql("DELETE FROM dsl_previews");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        counter.reset();
        assertThat(place(previews, first, "version: 2\n", adminId)).isEqualTo(1);
        assertThat(counter.statements()).as("first place statements").isEqualTo(2);
        assertThat(counter.connections()).as("first place connections").isEqualTo(1);

        counter.reset();
        assertThat(place(previews, second, "version: 2 # 2\n", adminId)).isEqualTo(1);
        assertThat(counter.statements()).as("later place statements").isEqualTo(1);
        assertThat(counter.connections()).as("later place connections").isEqualTo(1);

        assertThat(fixture.queryLong("SELECT COUNT(*) FROM dsl_previews")).isOne();
        DslPreviewRef ref = tx.execute(status -> previews.findRef()).orElseThrow();
        assertThat(ref.previewId()).isEqualTo(second);
    }

    private static int place(DslPreviewRepository previews, UUID previewId, String body, long adminId) {
        return tx.execute(status -> previews.place(
                previewId, body.getBytes(StandardCharsets.UTF_8), "c".repeat(64), DslSource.UPLOAD, adminId, NOW));
    }

    @Test
    @DisplayName(
            "savePreferences runs one statement on one connection, and an unknown user returns empty with one statement")
    void savePreferencesStatementCounts() {
        PortableFixture.Account member = fixture.createUser(false);
        UserRepository repository = app.context().getBean(UserRepository.class);

        counter.reset();
        Optional<UserRepository.SavedPreferences> saved =
                tx.execute(status -> repository.savePreferences(member.userId(), CHANGED, true, "Asia/Tokyo"));
        assertThat(counter.statements()).as("statements").isEqualTo(1);
        assertThat(counter.connections()).as("connections").isEqualTo(1);
        assertThat(saved).hasValueSatisfying(value -> {
            assertThat(value.getUserId()).isEqualTo(member.userId());
            assertThat(value.getDisplayTimeZone()).isEqualTo("Asia/Tokyo");
        });

        counter.reset();
        Optional<UserRepository.SavedPreferences> unknown =
                tx.execute(status -> repository.savePreferences(Long.MAX_VALUE, CHANGED, true, "Asia/Tokyo"));
        assertThat(unknown).isEmpty();
        assertThat(counter.statements()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "the zone stays when the change flag is false, is cleared by null with the flag true, and the other columns are saved")
    void savePreferencesZoneRules() {
        PortableFixture.Account member = fixture.createUser(false);
        UserRepository repository = app.context().getBean(UserRepository.class);
        tx.execute(status -> repository.savePreferences(member.userId(), CHANGED, true, "Asia/Tokyo"));

        Optional<UserRepository.SavedPreferences> kept =
                tx.execute(status -> repository.savePreferences(member.userId(), CHANGED, false, "UTC"));
        assertThat(kept)
                .hasValueSatisfying(
                        value -> assertThat(value.getDisplayTimeZone()).isEqualTo("Asia/Tokyo"));
        assertThat(zone(member.userId())).isEqualTo("Asia/Tokyo");

        Optional<UserRepository.SavedPreferences> cleared =
                tx.execute(status -> repository.savePreferences(member.userId(), CHANGED, true, null));
        assertThat(cleared)
                .hasValueSatisfying(
                        value -> assertThat(value.getDisplayTimeZone()).isNull());
        assertThat(zone(member.userId())).isNull();
        assertThat(fixture.queryString("SELECT display_name FROM users WHERE user_id = " + member.userId()))
                .isEqualTo("山田 花子");
        assertThat(fixture.queryString("SELECT theme FROM users WHERE user_id = " + member.userId()))
                .isEqualTo("dark");
    }

    private static String zone(long userId) {
        return fixture.queryString("SELECT display_time_zone FROM users WHERE user_id = " + userId);
    }
}
