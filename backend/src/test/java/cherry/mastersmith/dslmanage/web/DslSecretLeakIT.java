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
package cherry.mastersmith.dslmanage.web;

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.dslmanage.testsupport.DslApi;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * DSL の誤りの漏えいの防止（Intent 261015-deps-ui-carryover の FR3.1、前の Intent のレビューの dsl-v2 R-05）の結合テスト。
 *
 * <p>メソッドの呼び出しの追跡（TRACE）を有効にして、利用者が書いた値（目印）を持つ誤りのある DSL を投入の API に送り、読み込みの
 * 結果（{@code DslReadResult.Invalid}）と誤りの1件（{@code DslError}・{@code DslErrorItem}）が文字列にされても、目印がアプリのログに
 * 出ないことを確かめる。{@code TraceAspect} を外さず、本物の Spring の部品で流す（既存の {@code *SecretLeakIT} と同じ形）。応答の
 * 誤りの一覧に目印が出るのは、投入した本人に誤りの場所を示す決まった振る舞い（BR1.5）で、ここでは確かめない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AdminAccessTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class DslSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** 利用者が書いた値の目印。 */
    private static final String MARKER = "LEAK_MARKER_DSL_5c1e";

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

    @Autowired
    ActiveDslModelHolder activeDslModelHolder;

    @Autowired
    LoggingSystem loggingSystem;

    private DslApi api;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM dsl_previews");
        jdbc.update("DELETE FROM dsl_applied_revisions");
        activeDslModelHolder.replace(null);
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        api = new DslApi(port, users.accessToken(users.createAdmin()));
    }

    private String traced(CapturedOutput output, byte[] body) {
        int offset = output.getOut().length();
        HttpResponse<String> response;
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        try {
            response = api.submit(body, "PASTE");
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
        }
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("DSL_INVALID");
        return output.getOut().substring(offset) + output.getErr();
    }

    @Test
    @DisplayName(
            "with TRACE, a semantic error naming a user-written table leaks neither the name nor the built message")
    void semanticErrorLeaksNothing(CapturedOutput output) {
        byte[] body = DslYaml.dsl()
                .menu("表", "Table", MARKER)
                .table("dept_mst", column("code"))
                .bytes();

        String logs = traced(output, body);

        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("DslReader#read");
        assertThat(logs).as("誤りの種類は出る").contains("DslError[kind=");
        JsonLogRecords.assertContainsNoSecret(logs, MARKER);
        assertThat(logs).doesNotContain(MARKER);
    }

    @Test
    @DisplayName("with TRACE, a duplicate key written by the user leaks neither the key nor the path")
    void parseRejectionLeaksNothing(CapturedOutput output) {
        String yaml = new String(
                        DslYaml.dsl()
                                .menu("表", "Table", "dept_mst")
                                .table("dept_mst", column("code"))
                                .bytes(),
                        StandardCharsets.UTF_8)
                + MARKER + ": 1\n" + MARKER + ": 2\n";

        String logs = traced(output, yaml.getBytes(StandardCharsets.UTF_8));

        assertThat(logs).contains("DslReader#read").contains("DslError[kind=DUPLICATE_KEY");
        JsonLogRecords.assertContainsNoSecret(logs, MARKER);
        assertThat(logs).doesNotContain(MARKER);
    }
}
