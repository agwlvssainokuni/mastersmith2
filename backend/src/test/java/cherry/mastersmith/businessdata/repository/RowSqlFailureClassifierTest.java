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
import cherry.mastersmith.businessdata.domain.RowWriteFailure;
import cherry.mastersmith.businessdata.domain.RowWriteFailureReason;
import cherry.mastersmith.businessdata.domain.RowWriteStage;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
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

    @ParameterizedTest(name = "{0} {1} {2}/{3} delete={4} -> {5}")
    @CsvSource({
        // 番号の表（書き込みの文の段。reliability-design.md の 2節）
        "POSTGRESQL, WRITE, 23505, 0, false, DUPLICATE",
        "MYSQL, WRITE, 23000, 1062, false, DUPLICATE",
        "MARIADB, WRITE, 23000, 1062, false, DUPLICATE",
        "POSTGRESQL, WRITE, 23503, 0, false, REFERENCE_NOT_FOUND",
        "POSTGRESQL, WRITE, 23503, 0, true, REJECTED",
        "MYSQL, WRITE, 23000, 1452, false, REFERENCE_NOT_FOUND",
        "MYSQL, WRITE, 23000, 1451, true, REJECTED",
        "POSTGRESQL, WRITE, 23502, 0, false, NOT_NULL",
        "MYSQL, WRITE, 23000, 1048, false, NOT_NULL",
        "MARIADB, WRITE, HY000, 1364, false, NOT_NULL",
        "POSTGRESQL, WRITE, 22001, 0, false, REJECTED",
        "MYSQL, WRITE, 22001, 1406, false, REJECTED",
        "POSTGRESQL, WRITE, 22003, 0, false, REJECTED",
        "MYSQL, WRITE, 22001, 1264, false, REJECTED",
        "POSTGRESQL, WRITE, 23514, 0, false, REJECTED",
        "MYSQL, WRITE, HY000, 3819, false, REJECTED",
        "MARIADB, WRITE, 23000, 4025, false, REJECTED",
        "POSTGRESQL, WRITE, 22008, 0, false, REJECTED",
        "MYSQL, WRITE, 22001, 1292, false, REJECTED",
        "MYSQL, WRITE, HY000, 1366, false, REJECTED",
        "POSTGRESQL, WRITE, 22021, 0, false, REJECTED",
        "POSTGRESQL, WRITE, 55000, 0, false, REJECTED",
        "MARIADB, WRITE, HY000, 1471, false, REJECTED",
        // 形の誤り（D2: A）
        "POSTGRESQL, WRITE, 22P02, 0, false, REJECTED",
        "POSTGRESQL, WRITE, 22007, 0, false, REJECTED",
        "MYSQL, WRITE, 22007, 1525, false, REJECTED",
        "MARIADB, WRITE, 22018, 0, false, REJECTED",
        // Connector/J は類 22 を 22001（MysqlDataTruncation）にまとめる。番号の無い拒否（SIGNAL の 1644）も保存の拒否（G14）。
        "MYSQL, WRITE, 22001, 1644, false, REJECTED",
        // 競合（D1: A。どの段でも先に見る）
        "POSTGRESQL, WRITE, 40001, 0, false, CONFLICT",
        "POSTGRESQL, WRITE, 40P01, 0, false, CONFLICT",
        "POSTGRESQL, PRECHECK, 55P03, 0, false, CONFLICT",
        "MYSQL, WRITE, 40001, 1213, false, CONFLICT",
        "MARIADB, WRITE, HY000, 1205, false, CONFLICT",
        // 表・カラムが無い
        "POSTGRESQL, WRITE, 42P01, 0, false, MISMATCH",
        "POSTGRESQL, PRECHECK, 42703, 0, false, MISMATCH",
        "MYSQL, WRITE, 42S02, 1146, false, MISMATCH",
        "MARIADB, WRITE, 42S22, 1054, false, MISMATCH",
        // 時間切れ・断絶（D3: 書き込みの文までは UNAVAILABLE、確定の段だけ TIMEOUT。D4: 違反の後の段は UNAVAILABLE）
        "POSTGRESQL, PRECHECK, 57014, 0, false, UNAVAILABLE",
        "POSTGRESQL, WRITE, 57014, 0, false, UNAVAILABLE",
        "MYSQL, WRITE, 70100, 1969, false, UNAVAILABLE",
        "MARIADB, WRITE, 08S01, 0, false, UNAVAILABLE",
        "POSTGRESQL, COMMIT, 08006, 0, false, TIMEOUT",
        "MYSQL, COMMIT, HY000, 0, false, TIMEOUT",
        "POSTGRESQL, ROLLBACK_TO_SAVEPOINT, 57014, 0, false, UNAVAILABLE",
        "MYSQL, INSPECT, 08S01, 0, false, UNAVAILABLE",
        "MARIADB, ROLLBACK, 70100, 0, false, UNAVAILABLE",
        // 確定の段の遅延の制約の違反（D3: A）
        "POSTGRESQL, COMMIT, 23505, 0, false, REJECTED",
        // 違反の後の段のほかの失敗（D4: A。MariaDB のセーブポイントが無い 1305 を含む）
        "MARIADB, ROLLBACK_TO_SAVEPOINT, 42000, 1305, false, REJECTED",
        "POSTGRESQL, INSPECT, 42P01, 0, false, REJECTED",
        "MYSQL, ROLLBACK, HY000, 0, false, REJECTED",
        // 借りる段・結果の読み・表に無い番号
        "POSTGRESQL, BORROW, 28P01, 0, false, UNAVAILABLE",
        "POSTGRESQL, READ_RESULT, 22003, 0, false, UNEXPECTED",
        "POSTGRESQL, WRITE, 42601, 0, false, UNEXPECTED",
        "MYSQL, WRITE, 42000, 1064, false, UNEXPECTED"
    })
    @DisplayName("the write table decides the reason by database, stage and codes without reading the message")
    void writeTable(
            DatabaseProduct product,
            RowWriteStage stage,
            String sqlState,
            int vendorCode,
            boolean deleting,
            RowWriteFailureReason expected) {
        RowWriteFailure failure = RowSqlFailureClassifier.classifyWrite(
                product, stage, deleting, new SQLException("値 ペン を含む文", sqlState, vendorCode));

        assertThat(failure.reason()).isEqualTo(expected);
        assertThat(failure.stage()).isEqualTo(stage);
        assertThat(failure.sqlState()).isEqualTo(sqlState);
        assertThat(failure.vendorCode()).isEqualTo(vendorCode);
        assertThat(failure.toString()).doesNotContain("ペン");
    }

    @Test
    @DisplayName("write timeouts in the cause chain and unchecked exceptions are classified by stage")
    void writeTimeoutsAndUnchecked() {
        SQLException wrapped = new SQLException("x", null, 0, new SocketTimeoutException("Read timed out"));

        assertThat(RowSqlFailureClassifier.classifyWrite(DatabaseProduct.MYSQL, RowWriteStage.COMMIT, false, wrapped)
                        .reason())
                .isEqualTo(RowWriteFailureReason.TIMEOUT);
        assertThat(RowSqlFailureClassifier.classifyWrite(
                                DatabaseProduct.POSTGRESQL, RowWriteStage.WRITE, false, new SQLTimeoutException())
                        .reason())
                .isEqualTo(RowWriteFailureReason.UNAVAILABLE);
        RowWriteFailure unchecked = RowSqlFailureClassifier.classifyWrite(
                DatabaseProduct.POSTGRESQL, RowWriteStage.WRITE, false, new NumberFormatException("値 1x"));
        assertThat(unchecked.reason()).isEqualTo(RowWriteFailureReason.UNEXPECTED);
        assertThat(unchecked.exceptionClass()).isEqualTo("java.lang.NumberFormatException");
        RowWriteFailure inChain = RowSqlFailureClassifier.classifyWrite(
                DatabaseProduct.POSTGRESQL,
                RowWriteStage.WRITE,
                false,
                new IllegalStateException("x", new SQLException("y", "23505", 0)));
        assertThat(inChain.reason()).isEqualTo(RowWriteFailureReason.DUPLICATE);
        assertThat(inChain.exceptionClass()).isEqualTo("java.sql.SQLException");
    }
}
