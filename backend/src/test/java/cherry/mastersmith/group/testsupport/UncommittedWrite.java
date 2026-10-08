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
package cherry.mastersmith.group.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 別の接続で書き込みを未確定のまま持ち続ける手伝い（{@code reliability-design.md} 4.2 の #7。先の側を放さずに、後の側を H2 の上限で
 * 切らせる）。閉じると巻き戻して放す。
 */
public final class UncommittedWrite implements AutoCloseable {

    /** テストの内部DB の利用者。 */
    private static final String USER = "sa";

    /** テストの内部DB のパスワード（空）。 */
    private static final String PASSWORD = "";

    private final Connection connection;

    private UncommittedWrite(Connection connection) {
        this.connection = connection;
    }

    /**
     * 別の接続で書き込みを行い、確定させずに持ち続ける。
     *
     * @param url 接続の URL（{@code TestDatabase.url(dir)} の値）
     * @param sql 書き込みの文（引数は {@code ?}）
     * @param parameters 引数
     * @return 持ち続ける手伝い（閉じると巻き戻す）
     * @throws SQLException 接続か書き込みに失敗したとき
     */
    public static UncommittedWrite hold(String url, String sql, Object... parameters) throws SQLException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(sql, "sql");
        Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                statement.executeUpdate();
            }
            return new UncommittedWrite(connection);
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
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
