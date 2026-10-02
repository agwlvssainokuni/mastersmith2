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
package cherry.mastersmith.auth.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 組み込みの H2 で、ある文が実行中のまま（行の排他の確定を待っている）であることを、上限の時間つきで確かめるテストの手伝い。
 *
 * <p>H2 のセッションの一覧（{@code INFORMATION_SCHEMA.SESSIONS}）の実行中の文（{@code EXECUTING_STATEMENT}）を、別の接続から
 * 見回す。実時刻の sleep に頼らず、{@link Thread#onSpinWait()} で見回す。消した V8 の移行のテストにあった待ちの確かめ（文の
 * 絞り込み・接続・上限の時間が固定）を、引数で渡す形に書き直したもの。
 *
 * <p>接続の資格情報は、テストの内部DB の既定（利用者 {@code sa}、パスワードは空）を使う。
 */
public final class H2SessionWaits {

    /** テストの内部DB の利用者。 */
    private static final String USER = "sa";

    /** テストの内部DB のパスワード（空）。 */
    private static final String PASSWORD = "";

    private static final String EXECUTING =
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS" + " WHERE LOWER(EXECUTING_STATEMENT) LIKE LOWER(?)";

    private H2SessionWaits() {}

    /**
     * 絞り込みに合う文が実行中になるまで、上限の時間つきで待つ。
     *
     * <p>待っている側（排他の確定を待つはずの処理）が先に終わったとき、または上限の時間を過ぎても実行中にならなかったときは、
     * 何を待っていたか（絞り込みの文字だけ）を書いた {@link AssertionError} で失敗にする。
     *
     * @param url 接続の URL（{@code TestDatabase.url(dir)} の値）
     * @param statementPattern 実行中の文の絞り込み（{@code LIKE} の型。大文字・小文字をそろえて比べる）
     * @param timeout 上限の時間
     * @param waiterDone 待っている側が終わったかの判定（例: {@code future::isDone}）
     * @throws SQLException セッションの一覧を読めなかったとき
     */
    public static void awaitExecuting(String url, String statementPattern, Duration timeout, BooleanSupplier waiterDone)
            throws SQLException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(statementPattern, "statementPattern");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(waiterDone, "waiterDone");
        long deadline = System.nanoTime() + timeout.toNanos();
        try (Connection observer = DriverManager.getConnection(url, USER, PASSWORD);
                PreparedStatement executing = observer.prepareStatement(EXECUTING)) {
            executing.setString(1, statementPattern);
            while (System.nanoTime() < deadline) {
                if (waiterDone.getAsBoolean()) {
                    throw new AssertionError("待っている側が待ちに入らずに終わった（絞り込み: " + statementPattern + "）");
                }
                if (countExecuting(executing) > 0) {
                    return;
                }
                Thread.onSpinWait();
            }
        }
        throw new AssertionError("上限の時間 " + timeout + " のうちに文が実行中にならなかった（絞り込み: " + statementPattern + "）");
    }

    private static int countExecuting(PreparedStatement executing) throws SQLException {
        try (ResultSet rows = executing.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
