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
import cherry.mastersmith.businessdata.domain.RowWriteFailure;
import cherry.mastersmith.businessdata.domain.RowWriteFailureReason;
import cherry.mastersmith.businessdata.domain.RowWriteStage;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Objects;
import java.util.Set;

/**
 * 行の SQL の失敗の理由を、段階・SQLState・業者のエラーの番号・例外の型の連なりから決める純粋な部品（BR8.5・BR8.6、
 * reliability-design.md の 2節、NFR 設計のレビュー R-01・R-03、D1: A）。例外のメッセージを読まない。
 *
 * <p>読み取りの順: 時間切れ → 接続の失敗 → （結果を読む段階なら
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

    /** 競合による巻き戻しの SQLState（PostgreSQL の 40001 直列化の失敗・40P01 デッドロック・55P03 ロックの待ち切れ。D1: A）。 */
    static final Set<String> CONFLICT_STATES = Set.of("40001", "40P01", "55P03");

    /** 競合による巻き戻しの業者の番号（MySQL・MariaDB の 1213 デッドロック・1205 ロックの待ち切れ。D1: A）。 */
    static final Set<Integer> CONFLICT_VENDOR_CODES = Set.of(1213, 1205);

    /** 書き込みで、表・カラムが無いときの業者の番号（MySQL・MariaDB の 1146・1054）。 */
    static final Set<Integer> MISMATCH_VENDOR_CODES = Set.of(1146, 1054);

    /**
     * 書き込みの文の段で、DB が保存を拒んだ（409 {@code BUSINESS_SAVE_REJECTED}）PostgreSQL の SQLState（長さ 22001・桁あふれ 22003・
     * CHECK 23514・日時の範囲 22008・NUL 22021・更新できないビュー 55000、形の誤り 22P02・22007・22018。D2: A）。
     */
    static final Set<String> POSTGRES_REJECTED_STATES =
            Set.of("22001", "22003", "23514", "22008", "22021", "55000", "22P02", "22007", "22018");

    /**
     * 書き込みの文の段で、DB が保存を拒んだ MySQL・MariaDB の業者の番号（長さ 1406・桁あふれ 1264・CHECK 3819 と 4025・日時の範囲 1292・
     * 文字集合 1366・更新できないビュー 1471、削除の外部キー 1451）。
     */
    static final Set<Integer> MYSQL_REJECTED_VENDOR_CODES = Set.of(1406, 1264, 3819, 4025, 1292, 1366, 1471, 1451);

    /**
     * MySQL・MariaDB の形の誤りの SQLState（22007・22018。D2: A）。MySQL の Connector/J は、サーバーの類 22 の失敗を
     * {@code MysqlDataTruncation}（SQLState 22001）にまとめて返すため、22001 も含める（U2 の generation-notes.md の G14）。
     */
    static final Set<String> MYSQL_FORMAT_STATES = Set.of("22007", "22018", "22001");

    private RowSqlFailureClassifier() {}

    /**
     * 書き込みの失敗の理由を、DB の種類・段・操作・SQLState・業者の番号・例外の型の連なりから決める（Intent
     * 261016-business-data-views の U2、reliability-design.md の 2節の段ごとの表と番号の表、計画の D1〜D4）。例外のメッセージを読まない。
     *
     * <p>順: 借りる段 → UNAVAILABLE。競合（D1）→ CONFLICT。時間切れ・断絶 → 確定の段は TIMEOUT、ほかの段は UNAVAILABLE（D3・D4）。
     * 結果の読み → UNEXPECTED（R-02）。非検査の例外 → UNEXPECTED。違反の後の段（戻し・確かめ・巻き戻し）→ REJECTED（D4）。確定の段の
     * 類 23（遅延の制約の違反）→ REJECTED（D3）。表・カラムが無い → MISMATCH。そのあと番号の表で分け、表に無いものは UNEXPECTED。
     *
     * @param product DB の種類
     * @param stage 失敗した段
     * @param deleting 削除の文なら true（外部キーの違反を参照されている行の拒否として扱う）
     * @param failure 失敗（{@link SQLException} か非検査の例外）
     * @return 失敗（メッセージと原因を持たない）
     */
    public static RowWriteFailure classifyWrite(
            DatabaseProduct product, RowWriteStage stage, boolean deleting, Throwable failure) {
        Objects.requireNonNull(product, "product は必須です");
        Objects.requireNonNull(stage, "stage は必須です");
        Objects.requireNonNull(failure, "failure は必須です");
        SQLException sql = firstSql(failure);
        String sqlState = sql == null ? null : sql.getSQLState();
        Integer vendorCode = sql == null ? null : sql.getErrorCode();
        return new RowWriteFailure(
                writeReason(product, stage, deleting, failure, sql, sqlState, vendorCode),
                stage,
                sqlState,
                vendorCode,
                (sql == null ? failure : sql).getClass().getName());
    }

    private static RowWriteFailureReason writeReason(
            DatabaseProduct product,
            RowWriteStage stage,
            boolean deleting,
            Throwable failure,
            SQLException sql,
            String sqlState,
            Integer vendorCode) {
        boolean postgres = product == DatabaseProduct.POSTGRESQL;
        if (stage == RowWriteStage.BORROW) {
            return RowWriteFailureReason.UNAVAILABLE;
        }
        if (sql != null && conflict(postgres, sqlState, vendorCode)) {
            return RowWriteFailureReason.CONFLICT;
        }
        if (timedOut(failure) || connectionFailed(failure)) {
            return stage == RowWriteStage.COMMIT ? RowWriteFailureReason.TIMEOUT : RowWriteFailureReason.UNAVAILABLE;
        }
        if (stage == RowWriteStage.READ_RESULT || sql == null) {
            return RowWriteFailureReason.UNEXPECTED;
        }
        if (stage == RowWriteStage.ROLLBACK_TO_SAVEPOINT
                || stage == RowWriteStage.INSPECT
                || stage == RowWriteStage.ROLLBACK) {
            return RowWriteFailureReason.REJECTED;
        }
        if (stage == RowWriteStage.COMMIT) {
            return sqlState != null && sqlState.startsWith("23")
                    ? RowWriteFailureReason.REJECTED
                    : RowWriteFailureReason.TIMEOUT;
        }
        if ((sqlState != null && MISMATCH_STATES.contains(sqlState))
                || (!postgres && MISMATCH_VENDOR_CODES.contains(vendorCode))) {
            return RowWriteFailureReason.MISMATCH;
        }
        return postgres ? postgresWriteReason(sqlState, deleting) : mysqlWriteReason(sqlState, vendorCode, deleting);
    }

    private static boolean conflict(boolean postgres, String sqlState, Integer vendorCode) {
        return postgres
                ? sqlState != null && CONFLICT_STATES.contains(sqlState)
                : CONFLICT_VENDOR_CODES.contains(vendorCode);
    }

    private static RowWriteFailureReason postgresWriteReason(String sqlState, boolean deleting) {
        if (sqlState == null) {
            return RowWriteFailureReason.UNEXPECTED;
        }
        return switch (sqlState) {
            case "23505" -> RowWriteFailureReason.DUPLICATE;
            case "23503" -> deleting ? RowWriteFailureReason.REJECTED : RowWriteFailureReason.REFERENCE_NOT_FOUND;
            case "23502" -> RowWriteFailureReason.NOT_NULL;
            default ->
                POSTGRES_REJECTED_STATES.contains(sqlState)
                        ? RowWriteFailureReason.REJECTED
                        : RowWriteFailureReason.UNEXPECTED;
        };
    }

    private static RowWriteFailureReason mysqlWriteReason(String sqlState, Integer vendorCode, boolean deleting) {
        if (vendorCode != null) {
            switch (vendorCode) {
                case 1062 -> {
                    return RowWriteFailureReason.DUPLICATE;
                }
                case 1452 -> {
                    return deleting ? RowWriteFailureReason.REJECTED : RowWriteFailureReason.REFERENCE_NOT_FOUND;
                }
                case 1048, 1364 -> {
                    return RowWriteFailureReason.NOT_NULL;
                }
                default -> {
                    if (MYSQL_REJECTED_VENDOR_CODES.contains(vendorCode)) {
                        return RowWriteFailureReason.REJECTED;
                    }
                }
            }
        }
        return sqlState != null && MYSQL_FORMAT_STATES.contains(sqlState)
                ? RowWriteFailureReason.REJECTED
                : RowWriteFailureReason.UNEXPECTED;
    }

    private static SQLException firstSql(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException e) {
                return e;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

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
