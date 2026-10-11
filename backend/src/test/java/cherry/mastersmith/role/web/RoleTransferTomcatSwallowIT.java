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
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.testsupport.LargeRoleTransferYaml;
import cherry.mastersmith.role.testsupport.RawTransferPost;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 実際の Tomcat での本文の読み捨て（基盤の Q1: A、基盤の読み直しの R-01、計画の D-24、{@code server.tomcat.max-swallow-size}）の結合テスト。
 * 生のソケット（{@link RawTransferPost}）で要求の行・ヘッダー・本文をすべて書き切ってから応答を読み、10 MiB の本文を読まずに断った
 * 要求（入口の {@code ROLE_BUSY}・大きさの {@code PAYLOAD_TOO_LARGE}）の Problem Details が送り手に届くこと、その後の要求が通ることを
 * 確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestRoleBarrier.Config.class})
class RoleTransferTomcatSwallowIT {

    @Autowired
    TestRoleBarrier barrier;

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

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response)).containsEntry("code", code);
    }

    private static final int LIMIT = 10_485_760;

    @AfterEach
    void tearDown() {
        barrier.reset();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> problem(RawTransferPost.Response response) {
        assertThat(response.raw()).containsIgnoringCase("Content-Type: application/problem+json");
        return JsonMapper.shared().readValue(response.body(), Map.class);
    }

    @Test
    @DisplayName("a 10 MiB check while an apply holds the slot gets 409 ROLE_BUSY, and the next request passes")
    void busyAfterTheWholeBody() throws Exception {
        String name = RoleFixtures.uniqueName("読み捨て");
        long roleId = fixtures.role(name);
        fixtures.setting(roleId, PermissionTarget.schema("SALES"), MainPermission.READ);
        String yaml = "version: 1\nroles:\n  \"" + name + "\":\n    schemas:\n      SALES: {main: FULL}\n";
        String fingerprint = RoleApi.fingerprintOf(api.check(admin.token(), yaml));
        Gate gate = barrier.hold(Point.AFTER_LOCK, RoleOperation.APPLY_TRANSFER, String.valueOf(roleId));
        CompletableFuture<HttpResponse<String>> applying =
                CompletableFuture.supplyAsync(() -> api.apply(admin.token(), yaml, fingerprint));
        gate.awaitArrival();

        RawTransferPost.Response busy;
        try {
            busy = RawTransferPost.post(
                    port,
                    "/api/admin/role-transfer/check",
                    admin.token(),
                    LargeRoleTransferYaml.generateBytes(LargeRoleTransferYaml.Spec.of(20, 10_000, 30, LIMIT)));
        } finally {
            gate.release();
        }

        assertThat(busy.status()).isEqualTo(409);
        assertThat(problem(busy)).containsEntry("code", "ROLE_BUSY");
        assertThat(applying.get(TestRoleBarrier.WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS)
                        .statusCode())
                .isEqualTo(200);
        assertThat(api.check(admin.token(), "version: 1\nroles: {}\n").statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("a body slightly over the limit gets 413 PAYLOAD_TOO_LARGE after the whole body is written")
    void tooLargeAfterTheWholeBody() {
        RawTransferPost.Response tooLarge = RawTransferPost.post(
                port,
                "/api/admin/role-transfer/check",
                admin.token(),
                LargeRoleTransferYaml.generateBytes(LargeRoleTransferYaml.Spec.of(20, 10_000, 30, LIMIT + 1_024)));

        assertThat(tooLarge.status()).isEqualTo(413);
        assertThat(problem(tooLarge)).containsEntry("code", "PAYLOAD_TOO_LARGE");
        assertThat(api.check(admin.token(), "version: 1\nroles: {}\n").statusCode())
                .as("新しい接続の次の要求が通る")
                .isEqualTo(200);
    }
}
