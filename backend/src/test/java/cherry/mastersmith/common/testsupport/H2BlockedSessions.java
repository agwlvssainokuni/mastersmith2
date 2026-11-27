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
package cherry.mastersmith.common.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * 別の接続の待ち（排他の待ち）が始まったことを、H2 の {@code INFORMATION_SCHEMA.SESSIONS} で確かめて待つテストの手伝い
 * （Intent 261114-internal-db-postgresql の U2。実時刻の {@code sleep} を合否に使わず、同時の実行を待ち合わせで作るため）。
 *
 * <p>待つのは「待ちに入った接続が 1 本以上ある」ことだけで、上限を超えても入らなければ {@link AssertionError} にする。調べる
 * 間隔は短い一定の時間で、合否は経過の時間ではなく、待ちに入ったかで決める。
 */
public final class H2BlockedSessions {

    /** テストの内部DB の利用者。 */
    private static final String USER = "sa";

    /** テストの内部DB のパスワード（空）。 */
    private static final String PASSWORD = "";

    /** 待ちに入った接続の数を数える問い合わせ。 */
    private static final String COUNT_BLOCKED =
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL";

    /** 調べる間隔。 */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    private H2BlockedSessions() {}

    /**
     * 待ちに入った接続が現れるまで、上限つきで待つ。
     *
     * @param url 接続の URL（{@code TestDatabase.url(dir)} の値）
     * @param limit 待つ上限
     * @throws SQLException 調べられなかったとき
     * @throws AssertionError 上限までに待ちに入った接続が現れなかったとき
     */
    public static void awaitBlocked(String url, Duration limit) throws SQLException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(limit, "limit");
        long deadline = System.nanoTime() + limit.toNanos();
        try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
                Statement statement = connection.createStatement()) {
            while (true) {
                try (ResultSet rows = statement.executeQuery(COUNT_BLOCKED)) {
                    rows.next();
                    if (rows.getInt(1) > 0) {
                        return;
                    }
                }
                if (System.nanoTime() - deadline > 0) {
                    throw new AssertionError("待ちに入った接続が上限の時間までに現れなかった: " + limit);
                }
                LockSupport.parkNanos(POLL_INTERVAL.toNanos());
            }
        }
    }
}
