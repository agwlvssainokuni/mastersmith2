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
package cherry.mastersmith.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.auth.testsupport.AuthApi;
import cherry.mastersmith.auth.testsupport.TestSigningKeyEnvironmentPostProcessor;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * メソッドの呼び出しの追跡（TRACE）を有効にしても、パスワード・ハッシュ・トークン・署名鍵がログに出ないことの結合テスト
 * （NFR3.1、NFR3.5、NFR3.6、BR1.4、BR7.2）。
 */
@ExtendWith(OutputCaptureExtension.class)
class AuthSecretLeakIT {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("startup, login, failure, refresh and logout never write a secret to the log")
    void noSecretsInLog(CapturedOutput output) {
        String signingKey = TestSigningKeyEnvironmentPostProcessor.randomKey(32);
        String adminPassword = "初期管理者-" + TestDatabase.randomSecret();
        String email = "leak@example.com";

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(tempDir.resolve("leak")),
                        "--server.port=0",
                        "--mastersmith.auth.signing-key=" + signingKey,
                        "--mastersmith.auth.password.bcrypt-cost=4",
                        "--mastersmith.auth.initial-admin.email=" + email,
                        "--mastersmith.auth.initial-admin.password=" + adminPassword,
                        "--logging.level.cherry.mastersmith.auth=TRACE",
                        "--logging.level.cherry.mastersmith.user=TRACE")) {
            int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            AuthApi api = new AuthApi(port);
            String passwordHash = new JdbcTemplate(context.getBean(DataSource.class))
                    .queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);

            HttpResponse<String> login = api.login(email, adminPassword);
            String accessToken = AuthApi.accessToken(login);
            String cookie = AuthApi.cookieValue(login);
            HttpResponse<String> failed = api.login(email, "まちがったパスワード-9999");
            HttpResponse<String> refreshed = api.refresh(cookie, api.origin());
            String newCookie = AuthApi.cookieValue(refreshed);
            api.logout(newCookie, api.origin());

            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(failed.statusCode()).isEqualTo(401);
            assertThat(refreshed.statusCode()).isEqualTo(200);
            assertThat(output.getOut()).contains("ENTER LoginService#login");
            JsonLogRecords.assertAllLinesAreJson(output.getOut());
            JsonLogRecords.assertContainsNoSecret(
                    output.getAll(), adminPassword, passwordHash, accessToken, cookie, newCookie, signingKey);
            assertThat(failed.body())
                    .doesNotContain("まちがったパスワード-9999")
                    .doesNotContain(passwordHash)
                    .doesNotContain("USER_NOT_FOUND")
                    .doesNotContain("PASSWORD_MISMATCH");
        }
    }
}
