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
package cherry.mastersmith.businessdata.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 行の SQL の失敗の分け方（BR8.5・BR8.6、reliability-design.md の 2節、R-01・R-03・D1: A）の単体テスト。 */
class RowSqlFailureClassifierTest {

    @ParameterizedTest(name = "{0} {1}/{2} -> {3}")
    @CsvSource({
        // 定義との食い違い（3種類の DB の試しの値）
        "EXECUTE, 42P01, 0, MISMATCH",
        "EXECUTE, 42703, 0, MISMATCH",
        "EXECUTE, 42S02, 1146, MISMATCH",
        "EXECUTE, 42S22, 1054, MISMATCH",
        // 値の型の誤り（バインドと実行の段階だけ 400）
        "EXECUTE, 22P02, 0, INVALID_VALUE",
        "EXECUTE, 22003, 0, INVALID_VALUE",
        "BIND, 22007, 0, INVALID_VALUE",
        "EXECUTE, 22008, 0, INVALID_VALUE",
        "EXECUTE, 42883, 0, INVALID_VALUE",
        "EXECUTE, 22001, 1406, INVALID_VALUE",
        "EXECUTE, 22018, 0, INVALID_VALUE",
        "EXECUTE, HY000, 1366, INVALID_VALUE",
        "EXECUTE, HY000, 1525, INVALID_VALUE",
        "EXECUTE, HY000, 1105, UNEXPECTED",
        // 結果を読む段階の型の変換の失敗は 400 にせず READ_CONVERSION（D1: A）
        "READ, 22007, 0, READ_CONVERSION",
        "READ, S1009, 0, READ_CONVERSION",
        "READ, 42P01, 0, READ_CONVERSION",
        // 時間切れと接続の失敗
        "EXECUTE, 57014, 0, TIMEOUT",
        "READ, 57014, 0, TIMEOUT",
        "EXECUTE, 70100, 1969, TIMEOUT",
        "EXECUTE, 08S01, 0, UNAVAILABLE",
        "READ, 08006, 0, UNAVAILABLE",
        "EXECUTE, 08000, 0, UNAVAILABLE",
        // 貸し出しの始めの文の失敗は表によらず UNAVAILABLE（R-03）
        "SESSION_INIT, 42000, 1064, UNAVAILABLE",
        "SESSION_INIT, 57014, 0, UNAVAILABLE",
        // そのほか
        "EXECUTE, 42000, 1064, UNEXPECTED"
    })
    @DisplayName("the stage, SQLState and vendor code decide the reason without reading the message")
    void table(RowQueryStage stage, String sqlState, int vendorCode, RowQueryFailureReason expected) {
        RowQueryFailure failure =
                RowSqlFailureClassifier.classify(stage, new SQLException("値 ペン を含む文", sqlState, vendorCode));

        assertThat(failure.reason()).isEqualTo(expected);
        assertThat(failure.stage()).isEqualTo(stage);
        assertThat(failure.sqlState()).isEqualTo(sqlState);
        assertThat(failure.vendorCode()).isEqualTo(vendorCode);
        assertThat(failure.toString()).doesNotContain("ペン");
    }

    @Test
    @DisplayName("timeout exceptions and socket timeouts in the cause chain are TIMEOUT")
    void timeoutTypes() {
        assertThat(RowSqlFailureClassifier.classify(RowQueryStage.EXECUTE, new SQLTimeoutException())
                        .reason())
                .isEqualTo(RowQueryFailureReason.TIMEOUT);
        SQLException wrapped = new SQLException("x", "08S01", 0, new SocketTimeoutException("Read timed out"));
        assertThat(RowSqlFailureClassifier.classify(RowQueryStage.READ, wrapped).reason())
                .isEqualTo(RowQueryFailureReason.TIMEOUT);
    }

    @Test
    @DisplayName("a connection failure with an 08 state in the cause chain is UNAVAILABLE")
    void connectionCause() {
        SQLException outer = new SQLException("x", null, 0, new SQLTransientConnectionException("y", "08003"));

        assertThat(RowSqlFailureClassifier.classify(RowQueryStage.EXECUTE, outer)
                        .reason())
                .isEqualTo(RowQueryFailureReason.UNAVAILABLE);
    }

    @Test
    @DisplayName("unchecked exceptions are UNEXPECTED, except while reading where they are READ_CONVERSION")
    void uncheckedExceptions() {
        RowQueryFailure bind =
                RowSqlFailureClassifier.classify(RowQueryStage.BIND, new IllegalArgumentException("名前 secret"));
        RowQueryFailure read = RowSqlFailureClassifier.classify(RowQueryStage.READ, new ArithmeticException("x"));

        assertThat(bind.reason()).isEqualTo(RowQueryFailureReason.UNEXPECTED);
        assertThat(bind.sqlState()).isNull();
        assertThat(bind.vendorCode()).isNull();
        assertThat(bind.exceptionClass()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(read.reason()).isEqualTo(RowQueryFailureReason.READ_CONVERSION);
    }

    @Test
    @DisplayName("a SQL exception without a state is UNEXPECTED outside the read stage")
    void noState() {
        assertThat(RowSqlFailureClassifier.classify(RowQueryStage.EXECUTE, new SQLException("x"))
                        .reason())
                .isEqualTo(RowQueryFailureReason.UNEXPECTED);
    }
}
