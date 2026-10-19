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

import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Objects;
import java.util.Set;

/**
 * 行の SQL の失敗の理由を、段階・SQLState・業者のエラーの番号・例外の型の連なりから決める純粋な部品（BR8.5・BR8.6、
 * reliability-design.md の 2節、NFR 設計のレビュー R-01・R-03、D1: A）。例外のメッセージを読まない。
 *
 * <p>順: 貸し出しの始めの文の失敗は、分類の表によらず UNAVAILABLE（R-03）。それ以外は、時間切れ → 接続の失敗 → （結果を読む段階なら
 * READ_CONVERSION）→ 定義との食い違い → 値の型の誤り（バインドと実行の段階だけ。R-01）→ 想定外。時間切れの見分け方は既存の
 * {@code JdbcTargetSchemaReader.classify} とそろえる。
 */
public final class RowSqlFailureClassifier {

    /** PostgreSQL の、問い合わせの取り消し（待ちの上限による打ち切りを含む）の SQLState。 */
    static final String QUERY_CANCELED = "57014";

    /** MySQL・MariaDB の、問い合わせの中断（待ちの上限による打ち切り）の SQLState。 */
    static final String QUERY_INTERRUPTED = "70100";

    /** 定義との食い違いの SQLState（PostgreSQL の 42P01・42703、MySQL・MariaDB の 42S02/1146・42S22/1054）。 */
    static final Set<String> MISMATCH_STATES = Set.of("42P01", "42703", "42S02", "42S22");

    /**
     * 値の型の誤りの SQLState（PostgreSQL の 22P02・22003・22007・22008・42883、MySQL・MariaDB の 22001・22003・22007・22018）。
     */
    static final Set<String> INVALID_VALUE_STATES =
            Set.of("22P02", "22003", "22007", "22008", "42883", "22001", "22018");

    /** MySQL・MariaDB の HY000 のうち、値の型の誤りの業者のエラーの番号（1366・1525）。 */
    static final Set<Integer> INVALID_VALUE_VENDOR_CODES = Set.of(1366, 1525);

    private RowSqlFailureClassifier() {}

    /**
     * 失敗の理由を決める。
     *
     * @param stage 失敗した段階
     * @param failure 失敗（{@link SQLException} か非検査の例外）
     * @return 失敗（メッセージと原因を持たない）
     */
    public static RowQueryFailure classify(RowQueryStage stage, Throwable failure) {
        Objects.requireNonNull(stage, "stage は必須です");
        Objects.requireNonNull(failure, "failure は必須です");
        SQLException sql = failure instanceof SQLException e ? e : null;
        String sqlState = sql == null ? null : sql.getSQLState();
        Integer vendorCode = sql == null ? null : sql.getErrorCode();
        return new RowQueryFailure(
                reason(stage, failure, sqlState, vendorCode),
                stage,
                sqlState,
                vendorCode,
                failure.getClass().getName());
    }

    private static RowQueryFailureReason reason(
            RowQueryStage stage, Throwable failure, String sqlState, Integer vendorCode) {
        if (stage == RowQueryStage.SESSION_INIT) {
            return RowQueryFailureReason.UNAVAILABLE;
        }
        if (timedOut(failure)) {
            return RowQueryFailureReason.TIMEOUT;
        }
        if (connectionFailed(failure)) {
            return RowQueryFailureReason.UNAVAILABLE;
        }
        if (stage == RowQueryStage.READ) {
            return RowQueryFailureReason.READ_CONVERSION;
        }
        if (!(failure instanceof SQLException)) {
            return RowQueryFailureReason.UNEXPECTED;
        }
        if (sqlState != null && MISMATCH_STATES.contains(sqlState)) {
            return RowQueryFailureReason.MISMATCH;
        }
        if (sqlState != null
                && (INVALID_VALUE_STATES.contains(sqlState)
                        || ("HY000".equals(sqlState) && INVALID_VALUE_VENDOR_CODES.contains(vendorCode)))) {
            return RowQueryFailureReason.INVALID_VALUE;
        }
        return RowQueryFailureReason.UNEXPECTED;
    }

    private static boolean timedOut(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
            if (cause instanceof SQLException e
                    && (QUERY_CANCELED.equals(e.getSQLState()) || QUERY_INTERRUPTED.equals(e.getSQLState()))) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static boolean connectionFailed(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException e
                    && e.getSQLState() != null
                    && e.getSQLState().startsWith("08")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
