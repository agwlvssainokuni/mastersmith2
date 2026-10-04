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
package cherry.mastersmith.common.error.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.AuthTestTokens;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.TestUserAccounts;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * フィルターの中（認証の途中）で起きた想定外の例外の ERROR の行の数（Intent 261004-safety-carryover の FR4.1・FR4.1a・FR4.2・NFR6）の
 * 結合テスト。
 *
 * <p>このクラスの設定だけで接続のプールを1本・借りる待ちの上限を 250 ミリ秒（HikariCP の下限）にし、テストのスレッドがただ1本の接続を
 * 持ったまま、認証の要る API に Bearer つきの要求を1本送る。アクセストークンの認証の中で利用者を読む問い合わせが接続を借りられずに
 * 例外になり、フィルターの連なりの外へ出て {@code /error}（{@link ErrorPathController}）へ回る。持つ操作が終わってから要求を送るため、
 * {@code sleep} も待ち合わせも使わない（順序で確実に作る）。本番の設定とコードは変えない。
 *
 * <p>確かめること: 応答は 500 の Problem Details（{@code INTERNAL_ERROR}、内部の例外のメッセージを含まない、{@code traceId} を持つ）で、
 * その要求の ERROR の行は {@link ErrorPathController} の1行だけ（原因の例外と応答と同じ {@code traceId} を持つ）。Tomcat のロガー
 * （{@code org.apache.catalina.core.ContainerBase.} で始まる）の ERROR は0行。Tomcat の行は MDC の {@code traceId} を持たないため、
 * 要求を1本だけ送った区間の中の行として数える。定期の処理などがほかのロガーで出す ERROR は数えない。
 *
 * <p>Tomcat のロガーの名前（{@code ContainerBase.[エンジン名].[localhost].[/].[dispatcherServlet]}）のエンジン名は、同じ JVM で2つ目
 * 以降に作る組み込みの Tomcat では {@code Tomcat-<番号>} になる（Spring Boot の {@code TomcatWebServer}）。本番は1つの JVM に Tomcat が
 * 1つで {@code Tomcat} のため {@code application.yaml} は {@code [Tomcat]} の鍵で止めているが、結合テストは1つの JVM で多くの文脈を
 * 起動するため、名前が実行の順で変わる。そこで、{@code application.yaml} の {@code [Tomcat]} の鍵に設定されたレベル（OFF）を読み、
 * 動いている Tomcat の実際のエンジン名のロガーに同じレベルを当ててから確かめる（依頼者の決定 G2: A）。鍵の設定が無ければ失敗する。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.datasource.hikari.connection-timeout=250"
        })
@Import(AuthApiTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class FilterExceptionErrorLogIT {

    private static final String ERROR_PATH_LOGGER = ErrorPathController.class.getName();

    private static final String TOMCAT_LOGGER_PREFIX = "org.apache.catalina.core.ContainerBase.";

    /** {@code application.yaml} の {@code logging.level} で止めている Tomcat のロガーの名前（本番のエンジン名 {@code Tomcat}）。 */
    private static final String CONFIGURED_TOMCAT_LOGGER =
            "org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
        // Flyway は起動時に接続を2本同時に使うため、プールを1本にすると起動できない。Flyway にだけプールの外の接続を使わせる
        // （このクラスだけの設定。本番の設定は変えない）。
        registry.add("spring.flyway.url", () -> TestDatabase.url(tempDir));
        registry.add("spring.flyway.user", () -> "${spring.datasource.username}");
        registry.add("spring.flyway.password", () -> "${spring.datasource.password:}");
    }

    @LocalServerPort
    int port;

    @Value("${mastersmith.auth.signing-key}")
    String signingKey;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    MutableClock clock;

    @Autowired
    DataSource dataSource;

    @Autowired
    Environment environment;

    @Autowired
    LoggingSystem loggingSystem;

    @Autowired
    ServletWebServerApplicationContext webServerContext;

    /**
     * {@code application.yaml} の {@code [Tomcat]} の鍵のレベルを読み、動いている Tomcat の実際のエンジン名のロガーに当てる。
     *
     * @return レベルを当てたロガーの名前（実際のエンジン名が {@code Tomcat} なら当てる必要が無いため null）
     */
    private String applyConfiguredTomcatLevel() {
        Map<String, LogLevel> levels = Binder.get(environment)
                .bind("logging.level", Bindable.mapOf(String.class, LogLevel.class))
                .orElse(Map.of());
        assertThat(levels)
                .as("application.yaml の logging.level に Tomcat の dispatcherServlet のロガーの設定がある（FR4.2）")
                .containsEntry(CONFIGURED_TOMCAT_LOGGER, LogLevel.OFF);
        String engineName = ((TomcatWebServer) webServerContext.getWebServer())
                .getTomcat()
                .getEngine()
                .getName();
        if ("Tomcat".equals(engineName)) {
            return null;
        }
        String actualLogger = CONFIGURED_TOMCAT_LOGGER.replace("[Tomcat]", "[" + engineName + "]");
        loggingSystem.setLogLevel(actualLogger, levels.get(CONFIGURED_TOMCAT_LOGGER));
        return actualLogger;
    }

    @Test
    @DisplayName(
            "an exception inside the authentication filter is logged as exactly one ERROR by the error path controller")
    void filterExceptionIsLoggedOnce(CapturedOutput output) throws SQLException {
        String email = "filter-error-" + UUID.randomUUID() + "@example.com";
        long userId = TestUserAccounts.create(userAccountService, email, TestDatabase.randomSecret(), false);
        Instant now = clock.instant();
        String accessToken = new AuthTestTokens(signingKey).hs256(userId, now, now.plus(Duration.ofMinutes(5)));
        String appliedLogger = applyConfiguredTomcatLevel();

        HttpResponse<String> response;
        int outOffset;
        int errOffset;
        try (Connection held = dataSource.getConnection()) {
            assertThat(held.isValid(1)).isTrue();
            outOffset = output.getOut().length();
            errOffset = output.getErr().length();
            response = new HttpTestClient(port).get("/api/me/preferences", "Authorization", "Bearer " + accessToken);
        } finally {
            if (appliedLogger != null) {
                loggingSystem.setLogLevel(appliedLogger, null);
            }
        }
        String logs = output.getOut().substring(outOffset) + output.getErr().substring(errOffset);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        Map<String, Object> body = HttpTestClient.json(response);
        assertThat(body).containsEntry("status", 500).containsEntry("code", "INTERNAL_ERROR");
        Object traceId = body.get("traceId");
        assertThat(traceId).asString().matches("[0-9a-f]{32}");
        assertThat(response.body())
                .doesNotContain("Exception")
                .doesNotContain("Connection is not available")
                .doesNotContain("\tat ")
                .doesNotContain(email);

        List<Map<String, Object>> records = JsonLogRecords.parse(logs);
        List<Map<String, Object>> errorPath = records.stream()
                .filter(record -> "ERROR".equals(record.get("level")))
                .filter(record -> ERROR_PATH_LOGGER.equals(record.get("logger")))
                .toList();
        List<Map<String, Object>> tomcat = records.stream()
                .filter(record -> "ERROR".equals(record.get("level")))
                .filter(record -> String.valueOf(record.get("logger")).startsWith(TOMCAT_LOGGER_PREFIX))
                .toList();

        assertThat(errorPath).as("変換の境界の ERROR がちょうど1行").hasSize(1);
        assertThat(errorPath.getFirst())
                .containsEntry("message", "想定外のエラーが起きました")
                .containsEntry("code", "INTERNAL_ERROR")
                .containsEntry("traceId", traceId)
                .containsKey("exception");
        assertThat(tomcat).as("Tomcat のロガーの ERROR は出ない（同じ例外の二重の ERROR にならない）").isEmpty();
    }
}
