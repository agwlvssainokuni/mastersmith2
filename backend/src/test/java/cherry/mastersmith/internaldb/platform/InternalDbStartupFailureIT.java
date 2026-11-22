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

import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbStartupProbe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 内部DB の起動の失敗の結合テスト（FR1.2・FR1.3・FR3.10・NFR2.6・NFR4.4・T2・T3）。起動が止まり、ERROR がちょうど 1 件
 * （{@code Internal DB startup failed}）で、接続先・利用者名・パスワードが、アプリの全ロガーの出力と例外の連なりのどこにも出ない。
 */
@ExtendWith(OutputCaptureExtension.class)
class InternalDbStartupFailureIT {

    private static final String MESSAGE = "Internal DB startup failed";

    private static List<Map<String, Object>> startupErrors(CapturedOutput output) {
        return JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> "ERROR".equals(record.get("level")) && MESSAGE.equals(record.get("message")))
                .toList();
    }

    @Test
    @DisplayName("an unsupported kind of URL stops the start with UNSUPPORTED_KIND and leaks no URL, user or password")
    void unsupportedKindStops(CapturedOutput output) {
        String user = "u" + TestDatabase.randomSecret();
        String password = "p" + TestDatabase.randomSecret();
        String host = "h" + TestDatabase.randomSecret() + ".example.com";

        Throwable failure = InternalDbStartupProbe.startExpectingFailure(
                "--spring.datasource.url=jdbc:mysql://" + host + ":3306/leakdb",
                "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password);

        assertThat(InternalDbStartupProbe.findStartupException(failure))
                .get()
                .satisfies(e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.UNSUPPORTED_KIND));
        List<Map<String, Object>> errors = startupErrors(output);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .containsEntry("failureKind", "UNSUPPORTED_KIND")
                .containsEntry("settingName", "spring.datasource.url")
                .containsEntry("supportedKinds", "H2,POSTGRESQL");
        for (String secret : List.of(host, user, password, "leakdb")) {
            assertThat(output.getAll()).as("log output").doesNotContain(secret);
            assertThat(InternalDbStartupProbe.chainText(failure))
                    .as("exception chain")
                    .doesNotContain(secret);
        }
    }

    @Test
    @DisplayName("an empty URL stops the start with EMPTY_URL")
    void emptyUrlStops(CapturedOutput output) {
        Throwable failure = InternalDbStartupProbe.startExpectingFailure("--spring.datasource.url=");

        assertThat(InternalDbStartupProbe.findStartupException(failure))
                .get()
                .satisfies(e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.EMPTY_URL));
        List<Map<String, Object>> errors = startupErrors(output);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .containsEntry("failureKind", "EMPTY_URL")
                .containsEntry("settingName", "spring.datasource.url");
    }

    @Test
    @DisplayName("a PostgreSQL URL to a closed port stops the start with CONNECTION_FAILED (08001) and leaks nothing")
    void closedPortStops(CapturedOutput output) {
        int port = InternalDbStartupProbe.closedPort();
        String user = "u" + TestDatabase.randomSecret();
        String password = "p" + TestDatabase.randomSecret();
        String database = "d" + TestDatabase.randomSecret();

        Throwable failure = InternalDbStartupProbe.startExpectingFailure(
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + port + "/" + database,
                "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password);

        assertThat(InternalDbStartupProbe.findStartupException(failure))
                .get()
                .satisfies(e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.CONNECTION_FAILED));
        List<Map<String, Object>> errors = startupErrors(output);
        assertThat(errors).as("exactly one startup ERROR").hasSize(1);
        assertThat(errors.get(0))
                .containsEntry("failureKind", "CONNECTION_FAILED")
                .containsEntry("sqlState", "08001");
        assertThat((String) errors.get(0).get("exceptionTypes")).contains("org.postgresql.util.PSQLException");
        assertThat(errors.get(0)).doesNotContainKeys("settingName", "supportedKinds", "migrationVersion");
        for (String secret : List.of("127.0.0.1:" + port, ":" + port + "/", database, user, password)) {
            assertThat(output.getAll()).as("log output of every logger").doesNotContain(secret);
            assertThat(InternalDbStartupProbe.chainText(failure))
                    .as("exception chain")
                    .doesNotContain(secret);
        }
    }
}
