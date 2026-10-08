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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * U4 role への読み取りの口の問い合わせの数（NFR2.3、{@code performance-design.md} 1節、計画の D-8）の結合テスト。どの口も、グループの
 * 数・メンバーの数・所属の数に比例せず1回で読む。数えるのは既存の {@link SqlStatementCounter}（Hibernate が出す文）。
 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class GroupMembershipQueryCountIT {

    private static final Instant T0 = Instant.parse("2026-10-08T13:00:00Z");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    GroupMembershipQuery query;

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
    @DisplayName("the groups of a user are read with one statement for one group and for twenty groups")
    void groupsOfUser() {
        long few = fixtures.user("営業 一");
        long many = fixtures.user("営業 多");
        fixtures.member(fixtures.group(GroupFixtures.uniqueName("所属 一")), few, T0);
        for (int i = 0; i < 20; i++) {
            fixtures.member(fixtures.group(GroupFixtures.uniqueName("所属 多")), many, T0);
        }

        assertThat(statementsOf(() -> query.groupIdsOfUser(few))).containsExactly("select group_members");
        assertThat(statementsOf(() -> query.groupIdsOfUser(many))).containsExactly("select group_members");
    }

    @Test
    @DisplayName("member user ids are read with one statement for one member and for fifty members")
    void memberUserIds() {
        long small = fixtures.group(GroupFixtures.uniqueName("一人"));
        long large = fixtures.group(GroupFixtures.uniqueName("五十人"));
        fixtures.member(small, fixtures.user("営業 単"), T0);
        for (int i = 0; i < 50; i++) {
            fixtures.member(large, fixtures.user("営業 " + i), T0);
        }

        assertThat(statementsOf(() -> query.memberUserIds(Set.of(small)))).containsExactly("select groups");
        assertThat(statementsOf(() -> query.memberUserIds(Set.of(small, large))))
                .containsExactly("select groups");
    }

    @Test
    @DisplayName("existence and summaries are read with one statement for one group and for twenty groups")
    void existsAndSummaries() {
        Set<Long> twenty = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            twenty.add(fixtures.group(GroupFixtures.uniqueName("要約")));
        }
        long one = twenty.iterator().next();

        assertThat(statementsOf(() -> query.exists(one))).containsExactly("select groups");
        assertThat(statementsOf(() -> query.summaries(Set.of(one)))).containsExactly("select groups");
        assertThat(statementsOf(() -> query.summaries(twenty))).containsExactly("select groups");
    }
}
