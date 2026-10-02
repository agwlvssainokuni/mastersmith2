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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 別の接続で行を {@code SELECT ... FOR UPDATE} で持ち続けるテストの手伝い（Intent 260930-user-admin の U3、排他の待ちの上限切れを
 * 起こすため）。
 *
 * <p>アプリの接続のプールを使わず、テストの内部DB に JDBC で別に接続し、{@code autoCommit=false} で行の排他を持つ。閉じると巻き戻して
 * 接続を閉じる（{@code try-with-resources} で使う）。機能の間で使い回せるよう、共通の置き場（{@code common/testsupport}）に置く。
 */
public final class RowLockHolder implements AutoCloseable {

    /** テストの内部DB の利用者。 */
    private static final String USER = "sa";

    /** テストの内部DB のパスワード（空）。 */
    private static final String PASSWORD = "";

    private final Connection connection;

    private final int lockedRows;

    private RowLockHolder(Connection connection, int lockedRows) {
        this.connection = connection;
        this.lockedRows = lockedRows;
    }

    /**
     * 別の接続で、問い合わせに合う行を排他つきで読み、持ち続ける。
     *
     * @param url 接続の URL（{@code TestDatabase.url(dir)} の値）
     * @param selectForUpdate {@code SELECT ... FOR UPDATE} の文（引数は {@code ?}）
     * @param parameters 引数
     * @return 行を持つ手伝い（閉じると放す）
     * @throws SQLException 接続か問い合わせに失敗したとき
     */
    public static RowLockHolder hold(String url, String selectForUpdate, Object... parameters) throws SQLException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(selectForUpdate, "selectForUpdate");
        Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
        try {
            connection.setAutoCommit(false);
            int count = 0;
            try (PreparedStatement statement = connection.prepareStatement(selectForUpdate)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        count++;
                    }
                }
            }
            return new RowLockHolder(connection, count);
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    /**
     * 持っている行の数を返す（行を持てたことの確かめに使う）。
     *
     * @return 行の数
     */
    public int lockedRows() {
        return lockedRows;
    }

    @Override
    public void close() throws SQLException {
        try {
            connection.rollback();
        } finally {
            connection.close();
        }
    }
}
