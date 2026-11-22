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
package cherry.mastersmith.internaldb.testsupport;

import cherry.mastersmith.common.testsupport.TestDatabase;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 内部DB の PostgreSQL の結合テストが使う、実行ごとに一意の名前（{@code msidb_<乱数>}）の専用の空のデータベース（BR6.6）。同じ
 * コンテナの中に作り、閉じると {@code DROP DATABASE} で消す。途中で止まった前の実行の残りに頼らない。
 */
public final class InternalDbPostgresDatabase implements AutoCloseable {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String name;

    private final String jdbcUrl;

    private final String user;

    private final String password;

    private InternalDbPostgresDatabase(String name, String jdbcUrl, String user, String password) {
        this.name = name;
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    /**
     * 専用の空のデータベースを作る。
     *
     * @return 作ったデータベース
     */
    public static InternalDbPostgresDatabase create() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        String suffix = HexFormat.of().formatHex(bytes);
        String name = "msidb_" + suffix;
        // アプリの接続の利用者は、データベースごとに作る専用の利用者（所有者）。コンテナの管理者の名前を使わない。
        String user = "msiu_" + suffix;
        String password = TestDatabase.randomSecret();
        PostgreSQLContainer container = InternalDbPostgresContainer.get();
        try (Connection connection = InternalDbPostgresContainer.adminConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + user + " LOGIN PASSWORD '" + password + "'");
            statement.execute("CREATE DATABASE " + name + " OWNER " + user);
        } catch (SQLException e) {
            throw new IllegalStateException("the dedicated database could not be created", e);
        }
        String url = "jdbc:postgresql://" + container.getHost() + ":" + InternalDbPostgresContainer.mappedPort() + "/"
                + name;
        return new InternalDbPostgresDatabase(name, url, user, password);
    }

    /**
     * データベースの名前。
     *
     * @return 名前
     */
    public String name() {
        return name;
    }

    /**
     * 接続先の URL。
     *
     * @return JDBC の URL
     */
    public String jdbcUrl() {
        return jdbcUrl;
    }

    /**
     * 接続の利用者名。
     *
     * @return 利用者名
     */
    public String user() {
        return user;
    }

    /**
     * 接続のパスワード（コンテナの起動ごとの乱数）。
     *
     * @return パスワード
     */
    public String password() {
        return password;
    }

    /**
     * 専用のデータベースへの管理者の接続。
     *
     * @return 接続（呼び出し側が閉じる）
     * @throws SQLException 接続の失敗
     */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    /**
     * アプリの起動の引数のうち、内部DB の接続先・利用者・パスワード。
     *
     * @return {@code --spring.datasource.*} の引数
     */
    public String[] datasourceArgs() {
        return new String[] {
            "--spring.datasource.url=" + jdbcUrl,
            "--spring.datasource.username=" + user,
            "--spring.datasource.password=" + password
        };
    }

    /**
     * アプリの起動の引数（接続先・利用者・パスワードに、ランダムな番号の待ち受けと低い bcrypt の cost を足したもの）。
     *
     * @return 引数
     */
    public String[] applicationArgs() {
        List<String> args = new ArrayList<>(List.of(datasourceArgs()));
        args.add("--server.port=0");
        args.add("--mastersmith.auth.password.bcrypt-cost=4");
        return args.toArray(String[]::new);
    }

    @Override
    public void close() {
        try (Connection connection = InternalDbPostgresContainer.adminConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
            statement.execute("DROP ROLE IF EXISTS " + user);
        } catch (SQLException e) {
            throw new IllegalStateException("the dedicated database could not be dropped", e);
        }
    }
}
