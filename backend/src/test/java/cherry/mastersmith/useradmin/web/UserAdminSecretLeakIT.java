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
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 利用者の一覧と氏名・言語の変更で、個人に関する値と秘密が漏れないことの結合テスト（Intent 260930-user-admin の U3、AC1.1.6、
 * NFR3.1〜NFR3.4、BR7.4・BR7.5、team.md の「利用者の管理の漏えい」）。
 *
 * <p>1つの Spring の文脈の中で、{@code cherry.mastersmith} のロガーを TRACE（メソッドの呼び出しの追跡が有効）にした場合と、既定の INFO
 * の場合の両方で同じ要求を送り、その間に出た出力のどの行にも、利用者のメールアドレス・氏名・検索の文字・変更した氏名・パスワードの
 * ハッシュ値が無いことを確かめる。ロガーのレベルは Spring Boot の {@link LoggingSystem} で切り替え、終わったら設定の値に戻す
 * （計画 8節の D-10）。値は ASCII の乱数にして、JSON の書き方に左右されずに探せるようにする。前準備（利用者の作成・ログイン）の
 * 出力は確かめの範囲の外。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class UserAdminSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /**
     * Spring Data の repository の追跡のロガー（追跡は対象の実体のクラスの名前のロガーで出るため、Spring Data の repository の
     * インターフェースのメソッドは、このパッケージのロガーを TRACE にしたときに出る）。
     */
    private static final String SPRING_DATA_LOGGER = "org.springframework.data.jpa.repository.support";

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
    JdbcTemplate jdbc;

    @Autowired
    LoggingSystem loggingSystem;

    private UserAdminApi api;

    private UserAdminFixtures fixtures;

    private String adminEmail;

    private String admin;

    @BeforeEach
    void setUp() {
        api = new UserAdminApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        adminEmail = "leak-admin-" + TestDatabase.randomSecret() + "@example.com";
        fixtures.create(adminEmail, "Admin " + TestDatabase.randomSecret(), true);
        admin = fixtures.login(adminEmail);
    }

    /** 確かめの1回分の値と結果。 */
    private record Run(String logs, List<HttpResponse<String>> responses, String[] secrets, long userId) {}

    private Run run(CapturedOutput output) {
        String mark = TestDatabase.randomSecret();
        String email = "leak-check-" + mark + "@example.com";
        String name = "Leak " + TestDatabase.randomSecret();
        String newName = "Renamed " + TestDatabase.randomSecret();
        String badName = "Bad " + TestDatabase.randomSecret() + "\u0007";
        String tooLong = mark + "x".repeat(300);
        long userId = fixtures.create(email, name, false);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, userId);
        int audit = auditCount();
        int offset = output.getOut().length();

        List<HttpResponse<String>> responses = new ArrayList<>();
        responses.add(api.search(admin, mark));
        responses.add(api.search(admin, name.toUpperCase(java.util.Locale.ROOT)));
        responses.add(api.search(admin, tooLong));
        responses.add(api.putProfile(admin, String.valueOf(userId), newName, "en"));
        responses.add(api.putProfile(admin, String.valueOf(userId), badName, "ja"));
        responses.add(api.putProfile(admin, "999999999", newName, "ja"));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(200, 200, 400, 204, 400, 404);
        assertThat(auditCount()).as("一覧と氏名・言語の変更は監査に残らない").isEqualTo(audit);
        String logs = output.getOut().substring(offset) + output.getErr();
        String[] secrets = {email, name, mark, newName, badName.strip(), hash, adminEmail};
        return new Run(logs, responses, secrets, userId);
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    private void assertNoLeak(Run run) {
        JsonLogRecords.assertContainsNoSecret(run.logs(), run.secrets());
        assertThat(run.logs()).doesNotContain("$2a$");
        assertThat(run.responses().getFirst().body())
                .as("応答に一覧の値は出るが、ハッシュ値・失敗回数は出ない")
                .doesNotContain("$2a$")
                .doesNotContainIgnoringCase("hash")
                .doesNotContainIgnoringCase("failure");
        for (HttpResponse<String> response :
                run.responses().subList(2, run.responses().size())) {
            assertThat(response.body()).doesNotContain(Arrays.copyOf(run.secrets(), 5));
        }
    }

    private static List<String> lines(String logs, String needle) {
        return logs.lines().filter(line -> line.contains(needle)).toList();
    }

    @Test
    @DisplayName("with TRACE the method trace runs on every layer and shows *** instead of emails, names and searches")
    void traceLevel(CapturedOutput output) {
        loggingSystem.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        loggingSystem.setLogLevel(SPRING_DATA_LOGGER, LogLevel.TRACE);
        Run run;
        try {
            run = run(output);
        } finally {
            loggingSystem.setLogLevel(ROOT_LOGGER, null);
            loggingSystem.setLogLevel(SPRING_DATA_LOGGER, null);
        }

        assertNoLeak(run);
        JsonLogRecords.assertAllLinesAreJson(
                run.logs().substring(0, run.logs().length() - output.getErr().length()));
        for (String needle : new String[] {
            "ENTER UserAdminController#list",
            "ENTER UserAdminService#list",
            "ENTER UserAccountService#findAdminPage",
            "Repository#findAdminRowsBySearch",
            "Repository#countBySearch",
            "ENTER UserAdminController#updateProfile",
            "ENTER UserAccountService#updateProfile",
            "Repository#updateProfile"
        }) {
            assertThat(lines(run.logs(), needle)).as("追跡の行がある: %s", needle).isNotEmpty();
        }
        assertThat(lines(run.logs(), "ENTER UserAdminController#list"))
                .allSatisfy(line -> assertThat(line).contains("***"));
        assertThat(lines(run.logs(), "Repository#findAdminRowsBySearch"))
                .anySatisfy(line -> assertThat(line).contains("ENTER").contains("***"));
        assertThat(lines(run.logs(), "Repository#updateProfile"))
                .anySatisfy(line -> assertThat(line).contains("ENTER").contains("***"));
    }

    @Test
    @DisplayName("with the default INFO level nothing is traced and nothing personal is written either")
    void infoLevel(CapturedOutput output) {
        Run run = run(output);

        assertNoLeak(run);
        assertThat(lines(run.logs(), "ENTER ")).isEmpty();
    }
}
