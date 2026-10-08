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
import cherry.mastersmith.user.repository.UserRepository;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * グループの管理の7つの口の認可の表（7つの口 × 4つの主体 = 28 行。BR2.1、NFR1.1〜NFR1.3、AC2.1.6、{@code security-design.md} 2節）の
 * 結合テスト。{@code team.md} の必須のテスト（管理の API の認可）。
 *
 * <p>主体は未認証・管理者の印を持たない利用者・管理者・停止中の管理者。B3 では「要る権限だけを欠く利用者」はまだ作れないため、管理者の
 * 印を持たない利用者で 403 を確かめる（B5 で U4 が行を足す。計画の D-16）。停止中の管理者は、既存のアクセストークンの認証の入口で
 * 拒否されるため 401（{@code AUTHENTICATION_REQUIRED}）になる（Intent 260930-user-admin の決まり。利用者の管理の API と同じ）。拒否の後は
 * グループとメンバーの行を読み直し、変わっていないことを確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
class GroupAdminAuthorizationApiIT {

    /** 7つの口と、管理者のときの成功の状態コード。 */
    enum Endpoint {
        LIST(200),
        CREATE(201),
        DETAIL(200),
        RENAME(204),
        DELETE(204),
        ADD_MEMBER(204),
        REMOVE_MEMBER(204);

        private final int success;

        Endpoint(int success) {
            this.success = success;
        }
    }

    /** 主体。 */
    enum Subject {
        ANONYMOUS,
        MEMBER,
        ADMIN,
        SUSPENDED_ADMIN
    }

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
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private GroupApi api;

    private GroupActors actors;

    private GroupFixtures fixtures;

    @BeforeEach
    void setUp() {
        api = new GroupApi(port);
        actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        fixtures = new GroupFixtures(users, jdbc);
    }

    static Stream<Arguments> table() {
        List<Arguments> rows = new ArrayList<>();
        for (Endpoint endpoint : Endpoint.values()) {
            for (Subject subject : Subject.values()) {
                rows.add(Arguments.of(endpoint, subject));
            }
        }
        return rows.stream();
    }

    private String tokenOf(Subject subject) {
        return switch (subject) {
            case ANONYMOUS -> null;
            case MEMBER -> actors.member().token();
            case ADMIN -> actors.admin().token();
            case SUSPENDED_ADMIN -> actors.suspendedAdmin().token();
        };
    }

    private Map<String, Object> state(long groupId) {
        Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put(
                "groups",
                jdbc.queryForList("SELECT group_id, name, name_key, updated_at FROM groups ORDER BY group_id"));
        state.put(
                "members", jdbc.queryForList("SELECT group_id, user_id FROM group_members ORDER BY group_id, user_id"));
        state.put("target", groupId);
        return state;
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("table")
    void authorization(Endpoint endpoint, Subject subject) {
        long groupId = fixtures.group(GroupFixtures.uniqueName("認可"));
        Actor member = actors.user("見本 所属");
        Actor addable = actors.user("見本 追加");
        fixtures.member(groupId, member.userId(), Instant.parse("2026-10-08T00:00:00Z"));
        long emptyGroup = fixtures.group(GroupFixtures.uniqueName("認可 空"));
        String token = tokenOf(subject);
        Map<String, Object> before = state(groupId);

        HttpResponse<String> response =
                switch (endpoint) {
                    case LIST -> api.list(token, "");
                    case CREATE -> api.create(token, GroupFixtures.uniqueName("認可 作成"));
                    case DETAIL -> api.detail(token, groupId);
                    case RENAME -> api.rename(token, groupId, GroupFixtures.uniqueName("認可 改名"));
                    case DELETE -> api.delete(token, emptyGroup);
                    case ADD_MEMBER -> api.addMember(token, groupId, addable.userId());
                    case REMOVE_MEMBER -> api.removeMember(token, groupId, member.userId());
                };

        switch (subject) {
            case ADMIN -> assertThat(response.statusCode()).as(response.body()).isEqualTo(endpoint.success);
            case ANONYMOUS, SUSPENDED_ADMIN -> {
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(HttpTestClient.json(response)).containsEntry("code", "AUTHENTICATION_REQUIRED");
                assertThat(state(groupId)).as("拒否の後に状態は変わらない").isEqualTo(before);
            }
            case MEMBER -> {
                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(HttpTestClient.json(response)).containsEntry("code", "ACCESS_DENIED");
                assertThat(state(groupId)).as("拒否の後に状態は変わらない").isEqualTo(before);
            }
        }
    }
}
