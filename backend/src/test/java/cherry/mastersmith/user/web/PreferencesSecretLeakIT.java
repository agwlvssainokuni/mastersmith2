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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 表示のタイムゾーンの保存で、拒否された値（目印つき）と氏名がログ・応答に出ないことの結合テスト（Intent 261102-timezone-ui-polish の
 * U2、SD2・SD3、NFR2.3.3）。{@code cherry.mastersmith} のロガーを TRACE にしてメソッドの呼び出しの追跡を有効にしたうえで確かめる。
 * 400 のログは WARN 以下で、同じ要求について 1 回だけ出ることも確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mastersmith.auth.password.bcrypt-cost=4", "logging.level.cherry.mastersmith=TRACE"})
@ExtendWith(OutputCaptureExtension.class)
class PreferencesSecretLeakIT {

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

    @Test
    @DisplayName("a rejected zone value and the name never reach the logs or the responses")
    void rejectedValueDoesNotLeak(CapturedOutput output) {
        String email = "zone-leak-" + UUID.randomUUID() + "@example.com";
        String password = TestDatabase.randomSecret();
        TestUserAccounts.create(userAccountService, email, password, false);
        String accessToken = AuthApi.accessToken(new AuthApi(port).login(email, password));
        MeApi me = new MeApi(port);
        String markZone = "ZONEMARK" + UUID.randomUUID().toString().replace("-", "");
        String markName = "NAMEMARK" + UUID.randomUUID().toString().replace("-", "");
        int offset = output.getOut().length();

        List<HttpResponse<String>> responses = new ArrayList<>();
        responses.add(me.putPreferencesWithZone(accessToken, markName, "en", "dark", "lg", markZone));
        responses.add(me.putPreferencesWithZone(accessToken, markName, "en", "dark", "lg", 424242));
        responses.add(me.putPreferencesWithZone(accessToken, markName, "en", "dark", "lg", "Asia/Tokyo"));
        responses.add(me.getPreferences(accessToken));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(400, 400, 200, 200);
        String logs = output.getOut().substring(offset);
        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("ENTER UserPreferencesService#savePreferences");
        JsonLogRecords.assertAllLinesAreJson(logs);
        JsonLogRecords.assertContainsNoSecret(logs, markZone, markName, "424242", email);
        for (HttpResponse<String> response : responses.subList(0, 2)) {
            assertThat(response.body()).doesNotContain(markZone, markName, "424242");
        }
        long warnOrAbove400 = logs.lines()
                .filter(line -> line.contains("\"level\":\"ERROR\""))
                .count();
        assertThat(warnOrAbove400).as("拒否は ERROR にならない").isZero();
    }
}
