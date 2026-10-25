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
package cherry.mastersmith.businessdata.testsupport;

import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL・MariaDB の一般ログ（{@code general_log}。出力先は表）を使い、あるアカウントが対象DB に届けた文を、時刻の印の後から順に読む
 * テストだけの道具（Intent 261025-deps-perf-carryover の FR3.7 R-05）。
 *
 * <p>{@link StallingRelay} は暗号化された通信（MySQL・MariaDB の既定の TLS）では文の文字が見えず、止める段を「大きなまとまりの何番目」で
 * 指すしかない。まとまりの分け方はドライバーの版で変わるため、止めた後に、対象DB に届いた文を読んで止まった段が想定どおりかを確かめる
 * （{@link StallStage}）。止めた文そのものは対象DB に届かないため、一般ログに現れない。
 */
public final class StatementLog implements AutoCloseable {

    private final TargetDbTestDatabase database;

    /**
     * 一般ログを表に出す設定で有効にする。
     *
     * @param database テストの DB（MySQL・MariaDB。起動済み）
     */
    public StatementLog(TargetDbTestDatabase database) {
        this.database = database;
        database.execute("SET GLOBAL log_output = 'TABLE'", "SET GLOBAL general_log = 'ON'");
    }

    /**
     * 対象DB の今の時刻を、後で読む範囲の始まりの印として返す。
     *
     * @return 印（対象DB の時刻の文字）
     */
    public String mark() {
        try (Connection connection = database.admin();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT CAST(NOW(6) AS CHAR)")) {
            resultSet.next();
            return resultSet.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException("対象DB の時刻を読めませんでした", e);
        }
    }

    /**
     * 印の後に、指定のアカウントが対象DB に届けた文（問い合わせ）を古い順に返す。取り消し（{@code KILL ...}）は含めない。
     *
     * @param mark {@link #mark()} の印
     * @param user アカウントの名前
     * @return 文
     */
    public List<String> queriesSince(String mark, String user) {
        String sql = "SELECT CAST(argument AS CHAR) FROM mysql.general_log"
                + " WHERE event_time >= CAST(? AS DATETIME(6)) AND command_type = 'Query' AND user_host LIKE ?"
                + " ORDER BY event_time, thread_id";
        try (Connection connection = database.admin();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mark);
            statement.setString(2, user + "[%");
            List<String> queries = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String query = resultSet.getString(1);
                    // ドライバーが問い合わせの待ちの上限の後に別の接続から流す取り消し（MySQL の Connector/J の KILL QUERY）は、止めた
                    // 段の結果であって段の手がかりではないため数えない。
                    if (!query.startsWith("KILL ")) {
                        queries.add(query);
                    }
                }
            }
            return queries;
        } catch (SQLException e) {
            throw new IllegalStateException("一般ログを読めませんでした", e);
        }
    }

    /** 一般ログを止める。 */
    @Override
    public void close() {
        database.execute("SET GLOBAL general_log = 'OFF'");
    }
}
