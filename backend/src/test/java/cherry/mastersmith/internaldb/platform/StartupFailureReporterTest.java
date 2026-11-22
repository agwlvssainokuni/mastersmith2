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
package cherry.mastersmith.internaldb.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.internaldb.testsupport.LogCapture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 起動の失敗の分類と記録（BR5.1〜BR5.3・NFR2.2・NFR4.4。T4）。 */
class StartupFailureReporterTest {

    /** 例外の文に入れる、出力に出てはならない値（接続先・利用者名・パスワード・ホスト・ポート）。 */
    private static final String LEAKY =
            "jdbc:postgresql://secret-host.example.com:6543/secretdb user=secretuser password=secretpw";

    private static final List<String> SECRETS =
            List.of("secret-host.example.com", "6543", "secretdb", "secretuser", "secretpw");

    static Stream<Arguments> classificationTable() {
        return Stream.of(
                Arguments.of("08001", StartupFailureKind.CONNECTION_FAILED),
                Arguments.of("08006", StartupFailureKind.CONNECTION_FAILED),
                Arguments.of("28P01", StartupFailureKind.CONNECTION_FAILED),
                Arguments.of("28000", StartupFailureKind.CONNECTION_FAILED),
                Arguments.of("3D000", StartupFailureKind.CONNECTION_FAILED),
                Arguments.of("55P03", StartupFailureKind.MIGRATION_FAILED),
                Arguments.of("42P07", StartupFailureKind.UNKNOWN),
                Arguments.of("XX000", StartupFailureKind.UNKNOWN));
    }

    @ParameterizedTest(name = "[{index}] SQLState {0} is {1}")
    @MethodSource("classificationTable")
    @DisplayName("classifies a bare SQL exception by its SQLState")
    void classifiesBySqlState(String sqlState, StartupFailureKind expected) {
        StartupFailureReport report = StartupFailureReporter.classify(new SQLException(LEAKY, sqlState));

        assertThat(report.failureKind()).isEqualTo(expected);
        assertThat(report.sqlState()).isEqualTo(sqlState);
        assertThat(report.migrationVersion()).isNull();
        assertThat(report.exceptionTypes()).containsExactly("java.sql.SQLException");
    }

    @Test
    @DisplayName("an exception chain without a SQLState and without Flyway types is UNKNOWN")
    void unknownWithoutSqlState() {
        StartupFailureReport report =
                StartupFailureReporter.classify(new IllegalStateException(LEAKY, new RuntimeException(LEAKY)));

        assertThat(report.failureKind()).isEqualTo(StartupFailureKind.UNKNOWN);
        assertThat(report.sqlState()).isNull();
        assertThat(report.exceptionTypes())
                .containsExactly("java.lang.IllegalStateException", "java.lang.RuntimeException");
    }

    @Test
    @DisplayName("finds the SQLState inside a wrapped chain and prefers a connection failure over a lock timeout")
    void findsSqlStateInsideAChain() {
        Throwable chain =
                new RuntimeException("outer", new RuntimeException("middle", new SQLException(LEAKY, "08001")));

        assertThat(StartupFailureReporter.classify(chain).failureKind())
                .isEqualTo(StartupFailureKind.CONNECTION_FAILED);
        assertThat(StartupFailureReporter.classify(new RuntimeException(new SQLException("x", "55P03")))
                        .failureKind())
                .isEqualTo(StartupFailureKind.MIGRATION_FAILED);
    }

    @Test
    @DisplayName("stops walking the cause chain at the depth limit of 10 and survives a cycle")
    void depthLimitAndCycle() {
        Throwable deepest = new SQLException("deep", "08001");
        Throwable chain = deepest;
        for (int i = 0; i < 12; i++) {
            chain = new RuntimeException("level " + i, chain);
        }

        StartupFailureReport report = StartupFailureReporter.classify(chain);

        assertThat(report.exceptionTypes()).hasSize(StartupFailureReporter.MAX_DEPTH);
        // SQLState は深さ 10 の先にあるため見つからない。
        assertThat(report.sqlState()).isNull();
        assertThat(report.failureKind()).isEqualTo(StartupFailureKind.UNKNOWN);

        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertThat(StartupFailureReporter.classify(a).exceptionTypes()).hasSize(2);
    }

    @Test
    @DisplayName("a failed migration statement is MIGRATION_FAILED with the version number taken from the file name")
    void migrationFailureHasAVersion(@TempDir Path dir) throws IOException {
        write(dir, "V1__good.sql", "CREATE TABLE a (id INT);");
        write(dir, "V2__good.sql", "CREATE TABLE b (id INT);");
        write(dir, "V3__broken.sql", "THIS IS NOT SQL password=secretpw;");

        Throwable failure = failureOf(flyway(dir));

        StartupFailureReport report = StartupFailureReporter.classify(failure);

        assertThat(report.failureKind()).isEqualTo(StartupFailureKind.MIGRATION_FAILED);
        assertThat(report.migrationVersion()).isEqualTo(3);
        assertThat(report.exceptionTypes()).isNotEmpty();
    }

    @Test
    @DisplayName("a changed applied migration is MIGRATION_VALIDATION_FAILED with the version number")
    void validationFailureHasAVersion(@TempDir Path dir) throws IOException {
        write(dir, "V1__good.sql", "CREATE TABLE a (id INT);");
        String url = newUrl();
        flyway(dir, url).migrate();
        write(dir, "V1__good.sql", "CREATE TABLE a (id INT, name VARCHAR(10));");

        Throwable failure = failureOf(flyway(dir, url));

        StartupFailureReport report = StartupFailureReporter.classify(failure);

        assertThat(report.failureKind()).isEqualTo(StartupFailureKind.MIGRATION_VALIDATION_FAILED);
        assertThat(report.migrationVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "only digits are extracted from messages: the version is a number and the report holds no message text")
    void onlyDigitsAreTakenFromMessages() {
        Throwable failure = new RuntimeException(
                "Migration V12__" + LEAKY + " failed", new IllegalStateException("migration version 77 " + LEAKY));

        StartupFailureReport report = StartupFailureReporter.classify(failure);

        // 型の名前が違う（Flyway の例外ではない）ため、文に版の番号があっても分類にも番号にも使わない。
        assertThat(report.failureKind()).isEqualTo(StartupFailureKind.UNKNOWN);
        assertThat(report.migrationVersion()).isNull();
        assertThat(report.toString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("the log record carries only the allowed keys and none of the secrets in any value")
    void logOutputHasNoSecrets() {
        Throwable failure = new RuntimeException(LEAKY, new SQLException(LEAKY, "28P01"));
        try (LogCapture capture = LogCapture.start()) {
            StartupFailureReporter.log(StartupFailureReporter.classify(failure));

            List<ILoggingEvent> errors = capture.errorsWithMessage("Internal DB startup failed");
            assertThat(errors).hasSize(1);
            ILoggingEvent event = errors.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getKeyValuePairs())
                    .extracting(kv -> kv.key)
                    .containsExactly("failureKind", "exceptionTypes", "sqlState");
            for (String secret : SECRETS) {
                assertThat(capture.allText()).doesNotContain(secret);
            }
            assertThat(capture.allText())
                    .contains("failureKind=CONNECTION_FAILED")
                    .contains("sqlState=28P01");
        }
    }

    @Test
    @DisplayName("reports the setting name and the supported kinds for an empty or unsupported URL")
    void resolutionReports() {
        StartupFailureReport empty = StartupFailureReporter.forResolution(new InternalDbUrlResolution.Empty());
        StartupFailureReport unsupported =
                StartupFailureReporter.forResolution(new InternalDbUrlResolution.Unsupported());

        assertThat(empty.failureKind()).isEqualTo(StartupFailureKind.EMPTY_URL);
        assertThat(unsupported.failureKind()).isEqualTo(StartupFailureKind.UNSUPPORTED_KIND);
        for (StartupFailureReport report : List.of(empty, unsupported)) {
            assertThat(report.settingName()).isEqualTo("spring.datasource.url");
            assertThat(report.supportedKinds()).containsExactly("H2", "POSTGRESQL");
            assertThat(report.exceptionTypes()).isEmpty();
        }
        try (LogCapture capture = LogCapture.start()) {
            StartupFailureReporter.log(unsupported);

            ILoggingEvent event =
                    capture.errorsWithMessage("Internal DB startup failed").get(0);
            assertThat(event.getKeyValuePairs())
                    .extracting(kv -> kv.key)
                    .containsExactly("failureKind", "settingName", "supportedKinds");
        }
    }

    @Test
    @DisplayName("the startup exception carries only the kind, has no cause and never echoes message text")
    void startupExceptionHasNoCause() {
        InternalDbStartupException exception = new InternalDbStartupException(StartupFailureKind.CONNECTION_FAILED);

        assertThat(exception.getCause()).isNull();
        assertThat(exception.failureKind()).isEqualTo(StartupFailureKind.CONNECTION_FAILED);
        assertThat(exception.getMessage()).isEqualTo("Internal DB startup failed: CONNECTION_FAILED");
        assertThatThrownBy(() -> {
                    throw exception;
                })
                .hasNoCause();
    }

    private static String newUrl() {
        return "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private static Flyway flyway(Path dir) {
        return flyway(dir, newUrl());
    }

    /** 指定の H2 のメモリのデータベースを使う Flyway。 */
    private static Flyway flyway(Path dir, String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("filesystem:" + dir)
                .load();
    }

    private static Throwable failureOf(Flyway flyway) {
        try {
            flyway.migrate();
        } catch (RuntimeException e) {
            return e;
        }
        throw new AssertionError("migrate was expected to fail");
    }

    private static void write(Path dir, String name, String sql) throws IOException {
        Files.writeString(dir.resolve(name), sql, StandardCharsets.UTF_8);
    }
}
