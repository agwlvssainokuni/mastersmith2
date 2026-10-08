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
package cherry.mastersmith.role.web;

import static cherry.mastersmith.role.testsupport.RoleApi.entry;
import static cherry.mastersmith.role.testsupport.RoleApi.saveBody;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * ロールの管理と権限の設定の漏えいの防止（BR12.2・BR12.3、NFR1.7・NFR1.9、計画の D-5・R-13、{@code security-design.md} 4節）の結合テスト
 * （B4 の分）。メソッドの呼び出しの追跡（TRACE）を有効にして B4 の口を流し、操作した管理者のメールアドレス・パスワードがアプリのログに
 * 出ないこと、store の結果の型（{@code RoleStoreOutcome}）と1つ目の結果（{@code RoleFirstStep}）が種類だけで文字列にされ、値（エンティティ・
 * 名前の組）が出ないこと、応答と監査の行にパスワードのハッシュ値などが無いことを確かめる（既存の {@code *SecretLeakIT} と同じ形）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class RoleSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** 対象の名前の目印（消す操作と木の読み取りに使う）。 */
    private static final String MARKER = "LEAKCHECK_ROLE_7f3a";

    @Autowired
    LoggingSystem loggingSystem;

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

    private RoleApi api;

    private RoleFixtures fixtures;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    @Test
    @DisplayName("with TRACE, the role APIs leak no actor email or password, and outcomes print only their kind")
    void noSecretsInTheLog(CapturedOutput output) {
        String name = RoleFixtures.uniqueName("漏えい");
        long existing = fixtures.role(name);
        fixtures.setting(existing, PermissionTarget.table("SALES", MARKER), MainPermission.READ);

        int offset = output.getOut().length();
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        List<HttpResponse<String>> responses;
        try {
            HttpResponse<String> created = api.create(admin.token(), RoleFixtures.uniqueName("漏えい 作成"));
            long roleId = ((Number) HttpTestClient.json(created).get("roleId")).longValue();
            responses = List.of(
                    created,
                    api.create(admin.token(), name),
                    api.save(
                            admin.token(),
                            roleId,
                            saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", true, null)))),
                    api.tables(admin.token(), existing, "SALES"),
                    api.clear(
                            admin.token(),
                            existing,
                            RoleApi.json(
                                    Map.of("targets", List.of(Map.of("schemaName", "SALES", "tableName", MARKER))))),
                    api.list(admin.token(), ""),
                    api.detail(admin.token(), roleId),
                    api.delete(admin.token(), roleId));
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        assertThat(responses)
                .extracting(HttpResponse::statusCode)
                .containsExactly(201, 409, 204, 200, 204, 200, 200, 204);
        assertThat(logs)
                .as("メソッドの呼び出しの追跡が有効")
                .contains("ENTER RoleAdminService#savePermissions")
                .contains("RoleStoreTransactions#inFirst");
        JsonLogRecords.assertContainsNoSecret(
                logs,
                admin.email(),
                RoleActors.PASSWORD,
                "Done[value",
                "RoleDetail[roleId=" + Long.MAX_VALUE,
                "PermissionSetting[roleId=" + existing + ", schemaName");
        assertThat(logs)
                .as("store と1つ目の結果は種類だけ（値を持つ Done も値を出さない）")
                .contains("EXIT  RoleStoreTransactions#inFirst(): Done")
                .contains("EXIT  RoleStoreTransactions#inFirst(): Rejected[NAME_DUPLICATE]")
                .doesNotContain("Done[value=")
                .doesNotContain("Found[nodes=[");
        for (HttpResponse<String> response : responses) {
            assertThat(response.body())
                    .doesNotContain("$2a$")
                    .doesNotContainIgnoringCase("passwordHash")
                    .doesNotContainIgnoringCase("tokenHash")
                    .doesNotContainIgnoringCase("nameKey");
        }
        for (var row : jdbc.queryForList("SELECT * FROM audit_events WHERE event_type LIKE 'ROLE_%'")) {
            assertThat(String.valueOf(row.values()))
                    .doesNotContain(admin.email())
                    .doesNotContain("$2a$");
        }
    }
}
