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
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.group.testsupport.GroupActors;
import cherry.mastersmith.group.testsupport.GroupActors.Actor;
import cherry.mastersmith.group.testsupport.GroupApi;
import cherry.mastersmith.group.testsupport.GroupFixtures;
import cherry.mastersmith.group.testsupport.TestGroupBarrier;
import cherry.mastersmith.group.testsupport.TestGroupDeletionGuard;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
 * メンバーの個人に関する値の漏えいの防止（BR9.1・BR9.2、NFR1.6、AC2.1.12、{@code security-design.md} 4.2）の結合テスト。メソッドの
 * 呼び出しの追跡（TRACE）を有効にして詳細を読み、メンバーを足し外ししても、メンバーのメールアドレス・氏名がアプリのログに出ないこと、
 * 応答にパスワードのハッシュ値などが無いことを確かめる（既存の {@code *SecretLeakIT} と同じ形）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, TestGroupBarrier.Config.class, TestGroupDeletionGuard.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class GroupSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

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
    LoggingSystem loggingSystem;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("with TRACE, reading the detail and adding and removing members leak no member email or display name")
    void noPersonalValuesInTheLog(CapturedOutput output) {
        GroupApi api = new GroupApi(port);
        GroupActors actors = new GroupActors(userAccountService, revocationService, transactionManager, port);
        Actor admin = actors.admin();
        Actor taro = actors.user("漏えい 確かめ 太郎");
        Actor hanako = actors.user("漏えい 確かめ 花子");
        long groupId = ((Number) HttpTestClient.json(api.create(admin.token(), GroupFixtures.uniqueName("漏えい")))
                        .get("groupId"))
                .longValue();

        int offset = output.getOut().length();
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        List<HttpResponse<String>> responses;
        try {
            responses = List.of(
                    api.addMember(admin.token(), groupId, taro.userId()),
                    api.addMember(admin.token(), groupId, hanako.userId()),
                    api.detail(admin.token(), groupId),
                    api.list(admin.token(), ""),
                    api.removeMember(admin.token(), groupId, taro.userId()));
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(204, 204, 200, 200, 204);
        assertThat(responses.get(2).body()).contains(taro.email()).contains("漏えい 確かめ 花子");
        assertThat(logs)
                .as("メソッドの呼び出しの追跡が有効")
                .contains("ENTER GroupAdminService#detail")
                .contains("ENTER UserAccountService#findSummariesByIds");
        JsonLogRecords.assertContainsNoSecret(
                logs,
                taro.email(),
                hanako.email(),
                taro.email().toUpperCase(Locale.ROOT),
                "漏えい 確かめ 太郎",
                "漏えい 確かめ 花子",
                GroupActors.PASSWORD);
        for (HttpResponse<String> response : responses) {
            assertThat(response.body())
                    .doesNotContain("$2a$")
                    .doesNotContainIgnoringCase("passwordHash")
                    .doesNotContainIgnoringCase("tokenHash")
                    .doesNotContainIgnoringCase("consecutiveFailures")
                    .doesNotContainIgnoringCase("lockedUntil");
        }
        for (var row : jdbc.queryForList("SELECT * FROM audit_events WHERE event_type LIKE 'GROUP_%'")) {
            assertThat(String.valueOf(row.values()))
                    .doesNotContain(taro.email())
                    .doesNotContain(hanako.email())
                    .doesNotContain("漏えい 確かめ");
        }
    }
}
