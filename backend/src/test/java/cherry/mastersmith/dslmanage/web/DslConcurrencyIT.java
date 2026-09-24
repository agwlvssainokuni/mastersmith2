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
package cherry.mastersmith.dslmanage.web;

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.dslmanage.testsupport.DslApi;
import cherry.mastersmith.dslmanage.testsupport.DslAuditRows;
import cherry.mastersmith.dslmanage.testsupport.DslManageTestHooks;
import cherry.mastersmith.dslmanage.testsupport.DslManageTestHooks.BlockingGenerator;
import cherry.mastersmith.dslmanage.testsupport.DslManageTestHooks.ControlledRevisionRepository;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 同時の適用・適用の途中の失敗・重い処理の重なりの結合テスト（AC4.2.3・AC4.2.4、NFR8.1・NFR8.2・NFR1.13・NFR1.14）。重なりは
 * テスト用の差し込み口（{@link DslManageTestHooks}）で確実に作る。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AdminAccessTestConfig.class, DslManageTestHooks.class})
class DslConcurrencyIT {

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
    JdbcTemplate jdbc;

    @Autowired
    ControlledRevisionRepository revisions;

    @Autowired
    BlockingGenerator generator;

    @Autowired
    ActiveDslModelProvider activeDslModelProvider;

    @Autowired
    ActiveDslModelHolder activeDslModelHolder;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private DslApi api;

    private DslApi otherAdmin;

    private DslAuditRows audit;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM dsl_previews");
        jdbc.update("DELETE FROM dsl_applied_revisions");
        activeDslModelHolder.replace(null);
        revisions.reset();
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        api = new DslApi(port, users.accessToken(users.createAdmin()));
        otherAdmin = new DslApi(port, users.accessToken(users.createAdmin()));
        audit = new DslAuditRows(jdbc);
    }

    @AfterEach
    void tearDown() {
        revisions.reset();
        generator.release();
        executor.shutdownNow();
    }

    private int historyCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dsl_applied_revisions", Integer.class);
        return count == null ? 0 : count;
    }

    private long appliedEvents() {
        return audit.dslRows().stream()
                .filter(row -> row.eventType().equals("DSL_APPLIED"))
                .count();
    }

    @Test
    @DisplayName("two admins applying the same preview at once: exactly one succeeds and the history grows by one")
    void concurrentApply() throws Exception {
        String previewId = api.submitOk(DslYaml.dsl().table("t", column("c")).bytes());
        long appliedEventsBefore = appliedEvents();
        revisions.arm(2, Duration.ofSeconds(20));

        Future<HttpResponse<String>> first = executor.submit(() -> api.apply(previewId));
        Future<HttpResponse<String>> second = executor.submit(() -> otherAdmin.apply(previewId));
        List<Integer> statuses = List.of(
                first.get(60, TimeUnit.SECONDS).statusCode(),
                second.get(60, TimeUnit.SECONDS).statusCode());

        assertThat(revisions.allArrived()).as("両方が previewId の確かめを通って重なった").isTrue();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(historyCount()).isEqualTo(1);
        assertThat(appliedEvents()).isEqualTo(appliedEventsBefore + 1);
    }

    @Test
    @DisplayName("a failure while writing the history rolls everything back without switching or auditing")
    void failureRollsBack() {
        String previewId = api.submitOk(DslYaml.dsl().table("t", column("c")).bytes());
        revisions.failAfterCopy(true);
        long appliedEventsBefore = appliedEvents();

        HttpResponse<String> response = api.apply(previewId);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(DslApi.json(response)).containsEntry("code", "INTERNAL_ERROR");
        assertThat(historyCount()).as("写した行も巻き戻る").isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> preview =
                (Map<String, Object>) DslApi.json(api.get("/status")).get("preview");
        assertThat(preview).containsEntry("previewId", previewId);
        assertThat(activeDslModelProvider.current()).isInstanceOf(ActiveDsl.Absent.class);
        assertThat(appliedEvents()).as("巻き戻った適用は監査に残らない").isEqualTo(appliedEventsBefore);
        revisions.failAfterCopy(false);
        assertThat(api.apply(previewId).statusCode()).as("同じプレビューをあらためて適用できる").isEqualTo(200);
    }

    @Test
    @DisplayName("while a heavy operation runs, other heavy requests get 503 DSL_BUSY and change nothing")
    void busy() throws Exception {
        generator.block();
        CompletableFuture<HttpResponse<String>> running = CompletableFuture.supplyAsync(api::generate, executor);
        assertThat(generator.awaitEntered()).isTrue();
        int auditBefore = audit.count();
        String statusBefore = api.get("/status").body();

        HttpResponse<String> submit =
                otherAdmin.submit(DslYaml.dsl().table("u", column("c")).bytes(), "PASTE");
        HttpResponse<String> show = otherAdmin.get("/preview");
        HttpResponse<String> generate = otherAdmin.generate();
        HttpResponse<String> restore = otherAdmin.restore("44444444-4444-4444-4444-444444444444");

        for (HttpResponse<String> response : List.of(submit, show, generate, restore)) {
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(DslApi.json(response)).containsEntry("code", "DSL_BUSY");
        }
        assertThat(api.get("/status").body()).as("状態は変わらない").isEqualTo(statusBefore);
        assertThat(audit.count()).as("監査の出来事を出さない").isEqualTo(auditBefore);

        generator.release();
        assertThat(running.get(30, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
        assertThat(otherAdmin.get("/preview").statusCode()).as("許可は返されている").isEqualTo(200);
    }
}
