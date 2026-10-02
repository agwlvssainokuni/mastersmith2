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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 管理の API の外から、要求の本文の値を変えて自分の管理者の印・停止・失敗回数を変えられないこと（一括代入の防止）の結合テスト
 * （Intent 260930-user-admin の U3、NFR1.5、AC2.1.7、BR7.2）。team.md の必須のテスト（要求の改ざん）にあたる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class UserAdminMassAssignmentIT {

    private static final String NEW_PASSWORD = "改ざん確認の新しいパスワード-0001";

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
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private UserAdminFixtures fixtures;

    private MeApi me;

    private long memberId;

    private String member;

    @BeforeEach
    void setUp() {
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        me = new MeApi(port);
        String email = "member-" + UserAdminFixtures.marker() + "@example.com";
        memberId = fixtures.create(email, "一般 改ざん", false);
        member = fixtures.login(email);
        fixtures.lockState(memberId, 3, null);
    }

    private static Map<String, Object> forbiddenFields() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("admin", true);
        extra.put("adminFlag", true);
        extra.put("isAdmin", true);
        extra.put("suspended", false);
        extra.put("consecutiveFailures", 0);
        extra.put("lockedUntil", null);
        extra.put("userId", 1);
        return extra;
    }

    private void assertUnchanged(Map<String, Object> lockBefore) {
        assertThat(fixtures.userColumns(memberId))
                .containsEntry("ADMIN_FLAG", false)
                .containsEntry("SUSPENDED", false);
        assertThat(fixtures.lockColumns(memberId)).isEqualTo(lockBefore);
        assertThat(new UserAdminApi(port).list(member, "").statusCode())
                .as("管理の API は 403 のまま")
                .isEqualTo(403);
    }

    @Test
    @DisplayName(
            "PUT /api/me/preferences with extra flag, suspension and failure fields stays 200 and changes none of them")
    void preferences() {
        Map<String, Object> lockBefore = fixtures.lockColumns(memberId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "一般 改ざん後");
        body.put("language", "en");
        body.put("theme", "dark");
        body.put("fontSize", "lg");
        body.putAll(forbiddenFields());

        HttpResponse<String> response = me.putPreferencesRaw(member, UserAdminApi.json(body));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(HttpTestClient.json(response)).doesNotContainKeys("admin", "adminFlag", "suspended");
        assertThat(fixtures.userColumns(memberId))
                .containsEntry("DISPLAY_NAME", "一般 改ざん後")
                .containsEntry("LANGUAGE", "en");
        assertUnchanged(lockBefore);
    }

    @Test
    @DisplayName("POST /api/me/password with extra flag, suspension and failure fields changes only the password")
    void password() {
        Map<String, Object> lockBefore = fixtures.lockColumns(memberId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentPassword", UserAdminFixtures.PASSWORD);
        body.put("newPassword", NEW_PASSWORD);
        body.put("newPasswordConfirmation", NEW_PASSWORD);
        body.putAll(forbiddenFields());
        HttpTestClient client = new HttpTestClient(port);

        HttpResponse<String> response = client.send(client.request(MeApi.PASSWORD)
                .header("Authorization", "Bearer " + member)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(UserAdminApi.json(body)))
                .build());

        assertThat(response.statusCode()).isEqualTo(204);
        assertUnchanged(lockBefore);
    }

    @Test
    @DisplayName("a locked member cannot clear the lock through the preferences body")
    void lockedMemberCannotUnlockThemself() {
        fixtures.lockState(memberId, 5, clock.instant().plus(Duration.ofMinutes(15)));
        Map<String, Object> lockBefore = fixtures.lockColumns(memberId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "一般 改ざん");
        body.put("language", "ja");
        body.put("theme", "system");
        body.put("fontSize", "md");
        body.putAll(forbiddenFields());

        assertThat(me.putPreferencesRaw(member, UserAdminApi.json(body)).statusCode())
                .isEqualTo(200);

        assertUnchanged(lockBefore);
        assertThat(fixtures.lockColumns(memberId)).containsEntry("CONSECUTIVE_FAILURES", 5);
    }
}
