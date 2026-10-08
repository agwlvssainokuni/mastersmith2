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
package cherry.mastersmith.role.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import java.nio.file.Path;
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
 * 一覧と木の読み取りの問い合わせの数（NFR2.6、{@code performance-design.md} 1節、計画の D-8）の結合テスト。一覧は2回（件数・ページ。
 * 割り当ての数は B5 で足す）、木の1階層はロールの有無と設定の行の読み取りの2回で、ロールの数と設定の行の数に比例しないことを、既存の
 * {@link SqlStatementCounter} で数える。
 */
@SpringBootTest(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
class RoleAdminQueryCountIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @Autowired
    RoleAdminService service;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    JdbcTemplate jdbc;

    private RoleFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new RoleFixtures(jdbc);
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private static List<String> statementsOf(Supplier<?> call) {
        SqlStatementCounter.start();
        call.get();
        Map<String, List<String>> statements = SqlStatementCounter.stop();
        return statements.values().stream().flatMap(List::stream).toList();
    }

    @Test
    @DisplayName("a list page is read with the same two statements for one role and for a full page of twenty")
    void listStatements() {
        fixtures.role(RoleFixtures.uniqueName("数 一"));
        List<String> one = statementsOf(() -> service.list(null));
        for (int i = 0; i < 25; i++) {
            fixtures.role(RoleFixtures.uniqueName("数 多"));
        }

        List<String> full = statementsOf(() -> service.list(null));

        assertThat(one).containsExactly("select roles", "select roles");
        assertThat(full).isEqualTo(one);
        assertThat(((RoleListResult.Listed) service.list(null)).page().items()).hasSize(20);
    }

    @Test
    @DisplayName("each tree level reads the settings once, for one setting and for many")
    void treeStatements() {
        long small = fixtures.role(RoleFixtures.uniqueName("木 一"));
        fixtures.setting(small, PermissionTarget.schema("SALES"), MainPermission.READ);
        long large = fixtures.role(RoleFixtures.uniqueName("木 多"));
        fixtures.setting(large, PermissionTarget.schema("SALES"), MainPermission.READ);
        fixtures.setting(large, PermissionTarget.table("SALES", "ORDER_LINE"), MainPermission.FULL);
        for (int i = 0; i < 60; i++) {
            fixtures.setting(large, PermissionTarget.column("SALES", "ORDER_LINE", "C" + i), MainPermission.NONE);
            fixtures.setting(large, PermissionTarget.table("OLD", "T" + i), MainPermission.READ);
        }

        List<String> expected = List.of("select roles", "select permission_settings");
        assertThat(statementsOf(() -> service.schemas(small))).isEqualTo(expected);
        assertThat(statementsOf(() -> service.schemas(large))).isEqualTo(expected);
        assertThat(statementsOf(() -> service.tables(small, "SALES"))).isEqualTo(expected);
        assertThat(statementsOf(() -> service.tables(large, "OLD"))).isEqualTo(expected);
        assertThat(statementsOf(() -> service.columns(small, "SALES", "ORDER_LINE")))
                .isEqualTo(expected);
        assertThat(statementsOf(() -> service.columns(large, "SALES", "ORDER_LINE")))
                .isEqualTo(expected);
        assertThat(((PermissionTreeResult.Found) service.tables(large, "OLD")).nodes())
                .hasSize(60);
        assertThat(((PermissionTreeResult.Found) service.columns(large, "SALES", "ORDER_LINE")).nodes())
                .hasSize(63);
    }
}
