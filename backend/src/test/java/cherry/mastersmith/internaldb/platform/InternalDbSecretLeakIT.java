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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 内部DB の接続先・利用者名・パスワードが、TRACE を有効にしても（{@code TraceAspect} が各層の引数と戻り値を出しても）ログに出ない
 * ことの結合テスト（NFR4.12。{@code team.md} の {@code XxxSecretLeakIT} の形）。対応しない接続先と、閉じたポートの接続先の両方で起動を
 * 試みる。
 */
@ExtendWith(OutputCaptureExtension.class)
class InternalDbSecretLeakIT {

    @Test
    @DisplayName("with TRACE enabled, an unsupported URL with credentials leaks none of them to the log")
    void unsupportedUrlLeaksNothingUnderTrace(CapturedOutput output) {
        String user = "u" + TestDatabase.randomSecret();
        String password = "p" + TestDatabase.randomSecret();
        String host = "h" + TestDatabase.randomSecret() + ".example.com";

        Throwable failure = InternalDbStartupProbe.startExpectingFailure(
                "--logging.level.cherry.mastersmith=TRACE",
                "--spring.datasource.url=jdbc:mysql://" + user + ":" + password + "@" + host + ":3306/leakdb" + "?user="
                        + user + "&password=" + password,
                "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password);

        assertThat(InternalDbStartupProbe.findStartupException(failure)).isPresent();
        assertThat(JsonLogRecords.parse(output.getOut()))
                .as("TRACE is on, so the log is not trivially empty")
                .isNotEmpty();
        JsonLogRecords.assertContainsNoSecret(output.getAll(), user, password, host, "leakdb");
        JsonLogRecords.assertContainsNoSecret(
                InternalDbStartupProbe.chainText(failure), user, password, host, "leakdb");
    }

    @Test
    @DisplayName("with TRACE enabled, a closed-port PostgreSQL URL with credentials leaks none of them to the log")
    void closedPortLeaksNothingUnderTrace(CapturedOutput output) {
        int port = InternalDbStartupProbe.closedPort();
        String user = "u" + TestDatabase.randomSecret();
        String password = "p" + TestDatabase.randomSecret();
        String database = "d" + TestDatabase.randomSecret();

        Throwable failure = InternalDbStartupProbe.startExpectingFailure(
                "--logging.level.cherry.mastersmith=TRACE",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + port + "/" + database,
                "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password);

        assertThat(InternalDbStartupProbe.findStartupException(failure)).isPresent();
        List<String> secrets = List.of(user, password, database, "127.0.0.1:" + port);
        for (String secret : secrets) {
            assertThat(output.getAll()).doesNotContain(secret);
            assertThat(InternalDbStartupProbe.chainText(failure)).doesNotContain(secret);
        }
    }
}
