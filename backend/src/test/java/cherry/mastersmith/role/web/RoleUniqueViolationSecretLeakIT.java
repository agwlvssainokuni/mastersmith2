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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.role.testsupport.TestRoleBarrier;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Gate;
import cherry.mastersmith.role.testsupport.TestRoleBarrier.Point;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
 * 同じ名前の作成が重なって名前の鍵の一意の制約（{@code uk_roles_name_key}）の違反が起きても、H2 の違反の文（鍵の値を含む）が応答・アプリの
 * ログ・監査の行に出ないことの結合テスト（BR12.3、NFR1.8、{@code security-design.md} 4.1）。{@code application.yaml} の
 * {@code org.hibernate.orm.jdbc.error: OFF} が消されると落ちる。既定の INFO と、{@code cherry.mastersmith} を TRACE にした場合の両方で
 * 確かめる（group の {@code GroupUniqueViolationSecretLeakIT} と同じ形）。ロールの名前は個人に関する値として扱わないため、TRACE の引数に
 * 名前が出ることは確かめの対象にしない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class RoleUniqueViolationSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** H2 の違反の文の目印。 */
    private static final String[] VIOLATION_MARKERS = {
        "UK_ROLES_NAME_KEY", "Unique index or primary key violation", "could not execute statement"
    };

    @Autowired
    TestRoleBarrier barrier;

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

    @AfterEach
    void tearDown() {
        barrier.reset();
        loggingSystem.setLogLevel(ROOT_LOGGER, null);
    }

    private void overlap(CapturedOutput output) throws Exception {
        String name = RoleFixtures.uniqueName("違反 漏えい");
        long before = jdbc.queryForObject("SELECT COALESCE(MAX(audit_event_id), 0) FROM audit_events", Long.class);
        int offset = output.getOut().length();
        Gate later = barrier.hold(
                Point.AFTER_CHECK, RoleOperation.CREATE, RoleFixtures.name(name).key());

        CompletableFuture<HttpResponse<String>> b =
                CompletableFuture.supplyAsync(() -> api.create(admin.token(), name));
        later.awaitArrival();
        HttpResponse<String> a = api.create(admin.token(), name);
        later.release();
        HttpResponse<String> lost = b.get(TestRoleBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);

        assertThat(a.statusCode()).isEqualTo(201);
        assertThat(lost.statusCode()).as("違反が起きたことの確かめ").isEqualTo(409);
        String logs = output.getOut().substring(offset) + output.getErr();
        JsonLogRecords.assertContainsNoSecret(logs, VIOLATION_MARKERS);
        for (String marker : VIOLATION_MARKERS) {
            assertThat(lost.body()).doesNotContainIgnoringCase(marker);
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM audit_events WHERE audit_event_id > ?", before);
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            for (String marker : VIOLATION_MARKERS) {
                assertThat(String.valueOf(row.values())).doesNotContainIgnoringCase(marker);
            }
        }
    }

    @Test
    @DisplayName("with the default INFO level, an overlapping creation leaks no violation text")
    void infoLevel(CapturedOutput output) throws Exception {
        overlap(output);
    }

    @Test
    @DisplayName("with TRACE, an overlapping creation leaks no violation text")
    void traceLevel(CapturedOutput output) throws Exception {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        int offset = output.getOut().length();

        overlap(output);

        assertThat(output.getOut().substring(offset)).as("メソッドの呼び出しの追跡が有効").contains("ENTER RoleAdminService#create");
    }
}
