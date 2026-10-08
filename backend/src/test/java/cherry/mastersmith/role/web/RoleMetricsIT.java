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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

/**
 * ロールの管理の API の指標（{@code observability-design.md} 2節、NFR5.2）の結合テスト。独自の指標は足さず、既存の
 * {@code http.server.requests} が道の型を {@code uri} のタグにして出し、道の値（ID）を {@code uri} に入れないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleMetricsIT {

    @Autowired
    MeterRegistry meterRegistry;

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
    @DisplayName("requests of the ten endpoints are recorded under the path templates without ids")
    void uriTemplates() {
        long roleId = ((Number) HttpTestClient.json(api.create(admin.token(), RoleFixtures.uniqueName("指標")))
                        .get("roleId"))
                .longValue();
        fixtures.setting(roleId, PermissionTarget.table("SALES", "GONE"), MainPermission.READ);
        api.list(admin.token(), "");
        api.detail(admin.token(), roleId);
        api.rename(admin.token(), roleId, RoleFixtures.uniqueName("指標 改名"));
        api.schemas(admin.token(), roleId);
        api.tables(admin.token(), roleId, "SALES");
        api.columns(admin.token(), roleId, "SALES", "ORDER_LINE");
        api.save(
                admin.token(),
                roleId,
                saveBody("SALES", null, List.of(entry("SALES", null, null, "READ", null, null))));
        api.clear(
                admin.token(),
                roleId,
                RoleApi.json(Map.of("targets", List.of(Map.of("schemaName", "SALES", "tableName", "GONE")))));
        api.delete(admin.token(), roleId);

        Set<String> uris = meterRegistry.find("http.server.requests").timers().stream()
                .map(timer -> timer.getId().getTag("uri"))
                .filter(uri -> uri != null && uri.startsWith(RoleApi.PATH))
                .collect(Collectors.toSet());

        assertThat(uris)
                .containsExactlyInAnyOrder(
                        "/api/admin/roles",
                        "/api/admin/roles/{roleId}",
                        "/api/admin/roles/{roleId}/permissions",
                        "/api/admin/roles/{roleId}/permissions/schemas",
                        "/api/admin/roles/{roleId}/permissions/tables",
                        "/api/admin/roles/{roleId}/permissions/columns",
                        "/api/admin/roles/{roleId}/permissions/clear");
        assertThat(uris).noneMatch(uri -> uri.matches(".*/\\d+.*")).noneMatch(uri -> uri.contains("SALES"));
        Timer created = meterRegistry
                .find("http.server.requests")
                .tag("uri", "/api/admin/roles")
                .tag("method", "POST")
                .tag("status", "201")
                .timer();
        assertThat(created).isNotNull();
        assertThat(created.count()).isPositive();
    }
}
