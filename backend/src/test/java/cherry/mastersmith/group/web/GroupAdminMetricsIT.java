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
package cherry.mastersmith.group.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupActors;
import cherry.mastersmith.group.testsupport.GroupActors.Actor;
import cherry.mastersmith.group.testsupport.GroupApi;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.TestGroupBarrier;
import cherry.mastersmith.group.testsupport.TestGroupDeletionGuard;
import cherry.mastersmith.user.service.UserAccountService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * グループの管理の API の指標（{@code observability-design.md} 2節、NFR5.2）の結合テスト。独自の指標は足さず、既存の
 * {@code http.server.requests} が道の型を {@code uri} のタグにして出し、道の値（ID）を {@code uri} に入れないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupAdminMetricsIT {

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
    MeterRegistry meterRegistry;

    @Test
    @DisplayName("requests of the seven endpoints are recorded under the path templates without ids")
    void uriTemplates() {
        GroupApi api = new GroupApi(port);
        GroupActors actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        Actor admin = actors.admin();
        Actor user = actors.user("見本 指標");
        long groupId = ((Number) HttpTestClient.json(api.create(admin.token(), GroupFixtures.uniqueName("指標")))
                        .get("groupId"))
                .longValue();
        api.list(admin.token(), "");
        api.detail(admin.token(), groupId);
        api.rename(admin.token(), groupId, GroupFixtures.uniqueName("指標 改名"));
        api.addMember(admin.token(), groupId, user.userId());
        api.removeMember(admin.token(), groupId, user.userId());
        api.delete(admin.token(), groupId);

        Set<String> uris = meterRegistry.find("http.server.requests").timers().stream()
                .map(timer -> timer.getId().getTag("uri"))
                .filter(uri -> uri != null && uri.startsWith(GroupApi.PATH))
                .collect(Collectors.toSet());

        assertThat(uris)
                .containsExactlyInAnyOrder(
                        "/api/admin/groups",
                        "/api/admin/groups/{groupId}",
                        "/api/admin/groups/{groupId}/members",
                        "/api/admin/groups/{groupId}/members/{userId}");
        assertThat(uris).noneMatch(uri -> uri.matches(".*/\\d+.*"));
        Timer created = meterRegistry
                .find("http.server.requests")
                .tag("uri", "/api/admin/groups")
                .tag("method", "POST")
                .tag("status", "201")
                .timer();
        assertThat(created).isNotNull();
        assertThat(created.count()).isPositive();
    }
}
