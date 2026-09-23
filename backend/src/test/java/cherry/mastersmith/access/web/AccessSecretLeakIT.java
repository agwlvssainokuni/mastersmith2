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
package cherry.mastersmith.access.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * U3 の処理の追跡（TRACE）を有効にしても、アクセストークン・Authorization ヘッダー・メールアドレスがアプリのログに出ないことの
 * 結合テスト（NFR3.7、NFR10.3、NFR10.4）。
 */
@ExtendWith(OutputCaptureExtension.class)
class AccessSecretLeakIT {

    private static final String CRAFTED_PATH = "/api/admin/..;/secret";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("a denied and an unauthenticated admin request never write a token or an email to the log")
    void noSecretsInLog(CapturedOutput output) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(tempDir.resolve("access-leak")),
                        "--server.port=0",
                        "--mastersmith.auth.password.bcrypt-cost=4",
                        "--logging.level.cherry.mastersmith.access=TRACE")) {
            int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            HttpTestClient client = new HttpTestClient(port);
            AdminTestUsers users = new AdminTestUsers(context.getBean(UserAccountService.class), port);
            AdminTestUsers.TestUser member = users.createNonAdmin();
            String memberToken = users.accessToken(member);

            HttpResponse<String> denied =
                    client.get(AdminCheckController.PATH, "Authorization", "Bearer " + memberToken);
            HttpResponse<String> unauthenticated = client.get(AdminCheckController.PATH);
            HttpResponse<String> rejected = client.get(CRAFTED_PATH);

            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(unauthenticated.statusCode()).isEqualTo(401);
            assertThat(rejected.statusCode()).isEqualTo(400);
            assertThat(output.getAll()).doesNotContain(CRAFTED_PATH).doesNotContain("..;");
            JsonLogRecords.assertAllLinesAreJson(output.getOut());
            JsonLogRecords.assertContainsNoSecret(
                    output.getAll(), memberToken, member.email(), AdminTestUsers.PASSWORD);
            assertThat(output.getAll()).doesNotContain("Bearer ");
            assertThat(denied.body())
                    .doesNotContain(memberToken)
                    .doesNotContain(member.email())
                    .doesNotContain("Bearer");
            assertThat(unauthenticated.body()).doesNotContain("Bearer");

            List<Map<String, Object>> records = JsonLogRecords.parse(output.getOut());
            assertThat(records).anySatisfy(record -> assertThat(record).containsEntry("code", "ACCESS_DENIED"));
            assertThat(records)
                    .filteredOn(record -> "ACCESS_DENIED".equals(record.get("code")))
                    .allSatisfy(record ->
                            assertThat(record.get("traceId")).asString().isNotBlank());
        }
    }
}
