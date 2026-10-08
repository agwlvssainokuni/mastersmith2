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
package cherry.mastersmith.group.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 一覧と詳細の問い合わせの数（NFR2.6、{@code performance-design.md} 1節、計画の D-8）の結合テスト。一覧は 4 回（件数・ページ・メンバーの
 * 数・問う口。B3 の仮の実装は DB を読まないため Hibernate の文は 3 つ）、詳細は 3 回（グループ・メンバー・利用者の要約）で、グループの数と
 * メンバーの数に比例しないことを、既存の {@link SqlStatementCounter} で数える。
 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class GroupAdminListQueryCountIT {

    private static final Instant T0 = Instant.parse("2026-10-08T14:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    GroupAdminService service;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private GroupFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new GroupFixtures(users, jdbc);
    }

    private static List<String> statementsOf(Supplier<?> call) {
        SqlStatementCounter.start();
        call.get();
        Map<String, List<String>> statements = SqlStatementCounter.stop();
        return statements.values().stream().flatMap(List::stream).toList();
    }

    @Test
    @DisplayName("a list page is read with the same statements for one group and for a full page of twenty groups")
    void listStatements() {
        long first = fixtures.group(GroupFixtures.uniqueName("数 一"));
        fixtures.member(first, fixtures.user("見本 数"), T0);
        List<String> one = statementsOf(() -> service.list(null));
        for (int i = 0; i < 25; i++) {
            long groupId = fixtures.group(GroupFixtures.uniqueName("数 多"));
            fixtures.member(groupId, fixtures.user("見本 数 " + i), T0);
        }

        List<String> full = statementsOf(() -> service.list(null));

        assertThat(one).containsExactly("select groups", "select groups", "select group_members");
        assertThat(full).isEqualTo(one);
        assertThat(((GroupListResult.Listed) service.list(null)).page().items()).hasSize(20);
    }

    @Test
    @DisplayName("a detail is read with three statements for one member and for fifty members")
    void detailStatements() {
        long small = fixtures.group(GroupFixtures.uniqueName("詳細 一"));
        fixtures.member(small, fixtures.user("見本 一人"), T0);
        long large = fixtures.group(GroupFixtures.uniqueName("詳細 多"));
        for (int i = 0; i < 50; i++) {
            fixtures.member(large, fixtures.user("見本 多 " + i), T0.plusSeconds(i));
        }

        List<String> one = statementsOf(() -> service.detail(small));
        List<String> fifty = statementsOf(() -> service.detail(large));

        assertThat(one).containsExactly("select groups", "select group_members", "select users");
        assertThat(fifty).isEqualTo(one);
        assertThat(((GroupDetailResult.Found) service.detail(large)).members()).hasSize(50);
    }
}
