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
package cherry.mastersmith.navigation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * メニューの漏えいの防止（Intent 261015-deps-ui-carryover の FR4.1、前の Intent のレビューの navigation R-03）の結合テスト。
 *
 * <p>メソッドの呼び出しの追跡（TRACE）を有効にして {@code GET /api/me/navigation} を呼び、DSL の表示名（目印）がアプリのログに出ない
 * ことを確かめる。{@code TraceAspect} を外さず、本物の Spring の部品で流す（既存の {@code *SecretLeakIT} と同じ形）。テーブルの置き場の
 * 問い合わせ（{@code GET /api/me/table-access}）は Intent 261025-deps-perf-carryover の FR3.3 で削除した。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class NavigationSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    private static final String SCHEMA = "SALES";

    private static final String TABLE = "ORDERS";

    /** テーブルの表示名の目印。 */
    private static final String TABLE_LABEL = "LEAK_MARKER_NAV_TABLE_9d2b";

    /** メニューの項目の表示名の目印。 */
    private static final String MENU_LABEL = "LEAK_MARKER_NAV_MENU_9d2b";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    RefreshTokenRevocationService revocationService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LoggingSystem loggingSystem;

    private NavigationActors actors;

    private Actor reader;

    @BeforeEach
    void setUp() {
        RoleActors roleActors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        actors = new NavigationActors(roleActors, new RoleFixtures(jdbc), port);
        DslTable table = new DslTable(
                TABLE,
                new DisplayName(TABLE_LABEL + "_ja", TABLE_LABEL + "_en"),
                false,
                List.of("ID"),
                List.of(),
                Map.of("ID", RoleDslFixture.column("ID")));
        DslSchema schema = new DslSchema(SCHEMA, new DisplayName("販売", "Sales"), Map.of(TABLE, table));
        DslMenuItem child = new DslMenuItem(
                new DisplayName(MENU_LABEL + "_child_ja", MENU_LABEL + "_child_en"),
                null,
                new TableRef(SCHEMA, TABLE),
                List.of());
        DslMenuItem parent = new DslMenuItem(
                new DisplayName(MENU_LABEL + "_parent_ja", MENU_LABEL + "_parent_en"), null, null, List.of(child));
        NavigationDslFixture.install(holder, List.of(schema), List.of(parent));
        reader = actors.memberWithWorkRole(actors.schemaRole(SCHEMA, MainPermission.READ));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("with TRACE, reading the navigation leaks no display label")
    void noDisplayLabelInTheLog(CapturedOutput output) {
        int offset = output.getOut().length();
        HttpResponse<String> navigation;
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        try {
            navigation = actors.navigation(reader.token());
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        assertThat(navigation.statusCode()).as(navigation.body()).isEqualTo(200);
        assertThat(navigation.body()).as("応答は表示名をそのまま返す").contains(MENU_LABEL + "_child_ja");
        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("NavigationService#", "NavigationController#");
        JsonLogRecords.assertContainsNoSecret(logs, TABLE_LABEL, MENU_LABEL);
        assertThat(logs).doesNotContain(TABLE_LABEL).doesNotContain(MENU_LABEL);
    }
}
