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

import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Preferences;
import cherry.mastersmith.user.domain.ProfileUpdate;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.domain.Theme;
import cherry.mastersmith.user.domain.User;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者の一覧の問い合わせと氏名・言語の更新の結合テスト（Intent 260930-user-admin の U3、SD-4・BR1.1・BR1.2・BR1.5・BR1.6・BR5.2）。
 *
 * <p>内部DB はクラスごとの一時ディレクトリの H2 で、テストの間でデータを持ち越すため、各テストは自分だけが当たる印（乱数）を
 * メールアドレスか氏名に入れて、その印で絞って確かめる。
 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class UserAdminQueriesIT {

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

    private static final Instant BASE = Instant.parse("2026-09-22T00:00:00Z");

    private User save(String email, String displayName, Instant createdAt) {
        return repository.save(new User(
                email,
                "$2a$04$hash",
                false,
                createdAt,
                new Preferences(displayName, Language.JA, Theme.SYSTEM, FontSize.MD)));
    }

    private User saveNamed(String displayName) {
        return save("u-" + UUID.randomUUID() + "@example.com", displayName, BASE);
    }

    private static String marker() {
        return "m" + UUID.randomUUID().toString().replace("-", "");
    }

    private List<Long> searchIds(String q) {
        return repository.findAdminRowsBySearch(new SearchText(q).likePattern(), PageRequest.of(0, 1000)).stream()
                .map(UserAdminRow::userId)
                .toList();
    }

    @Test
    @DisplayName("the HQL ilike with escape and SpEL is accepted and maps to ILIKE on H2 (SD-4)")
    void ilikeQueryIsAccepted() {
        String mark = marker();
        save(mark + "-a@example.com", "テスト 太郎", BASE);
        save("other-" + UUID.randomUUID() + "@example.com", "Taro " + mark.toUpperCase(Locale.ROOT), BASE);

        SqlStatementCounter.start();
        long count = repository.countBySearch(new SearchText(mark).likePattern());
        List<UserAdminRow> rows =
                repository.findAdminRowsBySearch(new SearchText(mark).likePattern(), PageRequest.of(0, 20));
        SqlStatementCounter.stop();

        assertThat(count).isEqualTo(2);
        assertThat(rows).hasSize(2);
        List<String> sqls = SqlStatementCounter.recorded().stream()
                .map(r -> r.sql().toLowerCase(Locale.ROOT))
                .toList();
        assertThat(sqls).hasSize(2).allSatisfy(sql -> {
            assertThat(sql).contains(" ilike ?").contains("escape '\\'");
            assertThat(sql).doesNotContain(mark);
        });
    }

    @Test
    @DisplayName("rows are ordered by registration time and then by user id, with and without a search")
    void ordering() {
        String mark = marker();
        User late = save("late-" + mark + "@example.com", "late", BASE.plusSeconds(30));
        User tieB = save("tie-b-" + mark + "@example.com", "tie b", BASE.plusSeconds(10));
        User tieA = save("tie-a-" + mark + "@example.com", "tie a", BASE.plusSeconds(10));
        User early = save("early-" + mark + "@example.com", "early", BASE.minusSeconds(10));
        List<Long> expected = List.of(early.getUserId(), tieB.getUserId(), tieA.getUserId(), late.getUserId());

        Set<Long> mine = Set.copyOf(expected);
        List<Long> all = repository.findAdminRows(PageRequest.of(0, 10_000)).stream()
                .map(UserAdminRow::userId)
                .filter(mine::contains)
                .toList();

        assertThat(searchIds(mark)).containsExactlyElementsOf(expected);
        assertThat(all).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("pages hold 20 rows, the last page holds the rest and the count matches the rows")
    void paging() {
        String mark = marker();
        for (int i = 0; i < 21; i++) {
            save("page-" + i + "-" + mark + "@example.com", "page " + i, BASE.plusSeconds(i));
        }
        SearchText q = new SearchText(mark);

        List<UserAdminRow> first = repository.findAdminRowsBySearch(q.likePattern(), PageRequest.of(0, 20));
        List<UserAdminRow> second = repository.findAdminRowsBySearch(q.likePattern(), PageRequest.of(1, 20));
        List<UserAdminRow> beyond = repository.findAdminRowsBySearch(q.likePattern(), PageRequest.of(2, 20));

        assertThat(repository.countBySearch(q.likePattern())).isEqualTo(21);
        assertThat(first).hasSize(20);
        assertThat(first.getFirst().displayName()).isEqualTo("page 0");
        assertThat(second).extracting(UserAdminRow::displayName).containsExactly("page 20");
        assertThat(beyond).isEmpty();
        assertThat(repository.findAdminRows(PageRequest.of(0, 20))).hasSizeLessThanOrEqualTo(20);
        assertThat(repository.count()).isGreaterThanOrEqualTo(21);
    }

    @Test
    @DisplayName("a search hits the email or the name ignoring case for ASCII, full-width and accented letters")
    void caseInsensitive() {
        String mark = marker();
        User fullWidth = saveNamed("ＡＢＣ Taro " + mark);
        User accented = saveNamed("ÉCOLE Ärger " + mark);
        User byEmail = save("mail-" + mark + "@example.com", "メールで探す", BASE);

        assertThat(searchIds("ａｂｃ " + "taro " + mark)).containsExactly(fullWidth.getUserId());
        assertThat(searchIds("TARO " + mark.toUpperCase(Locale.ROOT))).containsExactly(fullWidth.getUserId());
        assertThat(searchIds("école ärger " + mark)).containsExactly(accented.getUserId());
        assertThat(searchIds("MAIL-" + mark.toUpperCase(Locale.ROOT))).containsExactly(byEmail.getUserId());
        assertThat(repository.countBySearch(new SearchText(mark).likePattern())).isEqualTo(3);
    }

    @Test
    @DisplayName("percent, underscore and backslash in the search match literally")
    void wildcardsAreLiteral() {
        String mark = marker();
        User literal = saveNamed("100% off_sale\\x " + mark);
        User lookalike = saveNamed("100X offXsale " + mark);

        assertThat(searchIds("100% off_sale\\x " + mark)).containsExactly(literal.getUserId());
        assertThat(searchIds("100%")).contains(literal.getUserId()).doesNotContain(lookalike.getUserId());
        assertThat(searchIds("off_sale")).contains(literal.getUserId()).doesNotContain(lookalike.getUserId());
        assertThat(searchIds("sale\\x")).contains(literal.getUserId()).doesNotContain(lookalike.getUserId());
        assertThat(repository.countBySearch(new SearchText("100% off_sale\\x " + mark).likePattern()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("known differences are fixed: dotted capital I does not match itself and sharp s differs from SS")
    void knownDifferences() {
        String mark = marker();
        User dotted = saveNamed("İstanbul " + mark);
        User sharp = saveNamed("Straße " + mark);
        User doubleS = saveNamed("STRASSE " + mark);

        assertThat(searchIds("İstanbul " + mark)).doesNotContain(dotted.getUserId());
        assertThat(searchIds("straße " + mark)).containsExactly(sharp.getUserId());
        assertThat(searchIds("strasse " + mark)).containsExactly(doubleS.getUserId());
    }

    @Test
    @DisplayName("the list queries never read the password hash column")
    void doesNotReadPasswordHash() {
        String mark = marker();
        saveNamed("hash " + mark);

        SqlStatementCounter.start();
        repository.findAdminRows(PageRequest.of(0, 20));
        repository.findAdminRowsBySearch(new SearchText(mark).likePattern(), PageRequest.of(0, 20));
        repository.countBySearch(new SearchText(mark).likePattern());
        SqlStatementCounter.stop();

        assertThat(SqlStatementCounter.recorded())
                .hasSize(3)
                .allSatisfy(r -> assertThat(r.sql().toLowerCase(Locale.ROOT)).doesNotContain("password_hash"));
    }

    @Test
    @DisplayName("updating the profile changes only the name and the language")
    void updateProfileChangesTwoColumns() {
        User user = repository.save(new User(
                "profile-" + UUID.randomUUID() + "@example.com",
                "$2a$04$hash",
                true,
                BASE,
                new Preferences("元の氏名", Language.JA, Theme.DARK, FontSize.LG)));
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT email, password_hash, admin_flag, suspended, theme, font_size FROM users WHERE user_id = ?",
                user.getUserId());

        Integer updated = tx.execute(
                status -> repository.updateProfile(user.getUserId(), new ProfileUpdate("新しい 氏名", Language.EN)));

        Map<String, Object> after = jdbc.queryForMap(
                "SELECT email, password_hash, admin_flag, suspended, theme, font_size FROM users WHERE user_id = ?",
                user.getUserId());
        assertThat(updated).isEqualTo(1);
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT display_name, language FROM users WHERE user_id = ?", user.getUserId()))
                .containsEntry("DISPLAY_NAME", "新しい 氏名")
                .containsEntry("LANGUAGE", "en");
    }

    @Test
    @DisplayName("updating an unknown user touches no row and the same values still count as one row")
    void updateProfileUnknownAndSame() {
        User user = saveNamed("同じ 氏名");

        Integer unknown =
                tx.execute(status -> repository.updateProfile(Long.MAX_VALUE, new ProfileUpdate("だれか", Language.JA)));
        Integer same = tx.execute(
                status -> repository.updateProfile(user.getUserId(), new ProfileUpdate("同じ 氏名", Language.JA)));

        assertThat(unknown).isZero();
        assertThat(same).isEqualTo(1);
    }
}
