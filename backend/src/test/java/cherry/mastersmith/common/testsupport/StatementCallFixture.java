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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 構造の検査（{@code BusinessDataBoundaryArchitectureTest}）が、決めた場所の外の JDBC の文の作成・実行を見つけて失敗することを示す
 * 確かめ用のクラス（Intent 261025-deps-perf-carryover の FR3.7 R-06）。アプリの外（{@code businessdata} の外の機能）が
 * {@code prepareCall} と {@code Statement.execute} 系を呼ぶ形を持つ。実行はしない（検査は呼び出しの形を見るだけ）。
 */
public final class StatementCallFixture {

    private StatementCallFixture() {}

    /**
     * ストアドの呼び出しを作る（{@code Connection.prepareCall}）。
     *
     * @param connection 接続
     * @return 呼び出し
     * @throws SQLException 作れないとき
     */
    public static CallableStatement call(Connection connection) throws SQLException {
        return connection.prepareCall("{call x()}");
    }

    /**
     * 文を実行する（{@code Statement.execute}）。
     *
     * @param statement 文
     * @return 結果
     * @throws SQLException 実行できないとき
     */
    public static boolean run(Statement statement) throws SQLException {
        return statement.execute("SELECT 1");
    }
}
