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
package cherry.mastersmith.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 自分の設定の API で、パスワード・ハッシュ・トークン・メールアドレスが漏れないことの結合テスト（NFR2.1〜NFR2.3、BR7.4・BR8.4、
 * team.md の秘密情報の漏えい）。
 *
 * <p>{@code cherry.mastersmith} のロガーを TRACE にしてメソッドの呼び出しの追跡を有効にしたうえで、U2 の API を呼んだ間に出たログ・
 * 監査の行・応答を確かめる（確かめる範囲は U2 が足すログ・応答・監査の行。前準備のログインのログは範囲の外。計画の unit-test-instructions
 * 5節）。値は ASCII の乱数にして、JSON の書き方に左右されずに探せるようにする。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mastersmith.auth.password.bcrypt-cost=4", "logging.level.cherry.mastersmith=TRACE"})
@ExtendWith(OutputCaptureExtension.class)
class MeSecretLeakIT {

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

    @Test
    @DisplayName(
            "password changes and preference saves write no password, hash, token or email to logs, audit rows or responses")
    void noSecretLeaks(CapturedOutput output) {
        String email = "leak-" + UUID.randomUUID() + "@example.com";
        String current = TestDatabase.randomSecret();
        String next = TestDatabase.randomSecret();
        String confirmation = TestDatabase.randomSecret();
        long userId = TestUserAccounts.create(userAccountService, email, current, false);
        AuthApi auth = new AuthApi(port);
        HttpResponse<String> login = auth.login(email, current);
        String accessToken = AuthApi.accessToken(login);
        String refreshToken = AuthApi.cookieValue(login);
        MeApi me = new MeApi(port);
        int offset = output.getOut().length();

        List<HttpResponse<String>> responses = new ArrayList<>();
        responses.add(me.changePassword(accessToken, current, next, confirmation));
        responses.add(me.changePassword(accessToken, confirmation, next, next));
        responses.add(me.changePassword(accessToken, current, next, next));
        responses.add(me.putPreferences(accessToken, "漏れの 確かめ", "en", "dark", "lg"));
        responses.add(me.putPreferences(accessToken, email, "EN", "system", "md"));
        responses.add(me.getPreferences(accessToken));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(400, 400, 204, 200, 400, 200);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
        String logs = output.getOut().substring(offset);
        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("ENTER UserPreferencesService#changePassword");
        JsonLogRecords.assertAllLinesAreJson(logs);
        String[] secrets = {current, next, confirmation, hash, accessToken, refreshToken, email};
        JsonLogRecords.assertContainsNoSecret(logs, secrets);
        assertThat(logs).doesNotContain("$2a$");
        for (HttpResponse<String> response : responses) {
            assertThat(response.body())
                    .doesNotContain(current, next, confirmation, hash, accessToken, refreshToken, email);
        }
        List<Map<String, Object>> auditRows =
                jdbc.queryForList("SELECT * FROM audit_events WHERE event_type = 'PASSWORD_CHANGED'");
        assertThat(auditRows).hasSize(2);
        assertThat(auditRows.toString())
                .doesNotContain(current, next, confirmation, hash, accessToken, refreshToken, email)
                .doesNotContain("$2a$");
    }
}
