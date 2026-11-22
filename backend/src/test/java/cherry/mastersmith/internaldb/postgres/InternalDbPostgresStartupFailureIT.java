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
package cherry.mastersmith.internaldb.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.internaldb.platform.StartupFailureKind;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresContainer;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresDatabase;
import cherry.mastersmith.internaldb.testsupport.InternalDbPostgresIT;
import cherry.mastersmith.internaldb.testsupport.InternalDbStartupProbe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

/**
 * 内部DB を PostgreSQL で動かす経路の、接続の失敗の結合テスト（FR3.10・NFR2.6・NFR4.4）。パスワードが違うと起動が止まり、
 * {@code CONNECTION_FAILED} の ERROR がちょうど 1 件で、アプリの全ロガー（Hikari・Spring を含む）の出力に URL・利用者名・パスワード・
 * ホスト・ポートが出ない。
 */
@InternalDbPostgresIT
class InternalDbPostgresStartupFailureIT {

    private static final String MESSAGE = "Internal DB startup failed";

    @Test
    @DisplayName("a wrong password stops the start with CONNECTION_FAILED (28P01) and leaks no connection value")
    void wrongPasswordStopsTheStart(CapturedOutput output) {
        try (InternalDbPostgresDatabase database = InternalDbPostgresDatabase.create()) {
            String wrongPassword = "wrong-" + TestDatabase.randomSecret();

            Throwable failure = InternalDbStartupProbe.startExpectingFailure(
                    "--spring.datasource.url=" + database.jdbcUrl(),
                    "--spring.datasource.username=" + database.user(),
                    "--spring.datasource.password=" + wrongPassword);

            assertThat(InternalDbStartupProbe.findStartupException(failure))
                    .get()
                    .satisfies(e -> assertThat(e.failureKind()).isEqualTo(StartupFailureKind.CONNECTION_FAILED));
            List<Map<String, Object>> errors = JsonLogRecords.parse(output.getOut()).stream()
                    .filter(record -> "ERROR".equals(record.get("level")) && MESSAGE.equals(record.get("message")))
                    .toList();
            assertThat(errors).as("exactly one startup ERROR").hasSize(1);
            assertThat(errors.get(0))
                    .containsEntry("failureKind", "CONNECTION_FAILED")
                    .containsEntry("sqlState", "28P01");
            assertThat((String) errors.get(0).get("exceptionTypes")).contains("org.postgresql.util.PSQLException");
            String port = String.valueOf(InternalDbPostgresContainer.mappedPort());
            List<String> secrets = List.of(
                    database.jdbcUrl(),
                    database.name(),
                    database.user(),
                    database.password(),
                    wrongPassword,
                    ":" + port);
            for (String secret : secrets) {
                assertThat(output.getAll()).as("log output of every logger").doesNotContain(secret);
                assertThat(InternalDbStartupProbe.chainText(failure))
                        .as("exception chain")
                        .doesNotContain(secret);
            }
        }
    }
}
