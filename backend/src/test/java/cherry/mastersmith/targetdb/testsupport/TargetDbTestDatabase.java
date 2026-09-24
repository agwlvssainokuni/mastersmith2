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
package cherry.mastersmith.targetdb.testsupport;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.SqlIdentifiers;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * 対象DB の結合テストで使う、コンテナで起動した本物の DB（NFR12.1・NFR12.2、project.md の Mandated）。
 *
 * <p>テストのクラスごとに1つ起動し、名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と、読み取りの権限だけの
 * アカウントを作って、クラスの終わりに消す。表を作る操作は巻き戻せないため、トランザクションの巻き戻しには頼らない。
 * 管理者と読み取りのアカウントのパスワードは、実行のたびに乱数で作る（コードに固定の値を書かない）。
 */
public abstract class TargetDbTestDatabase {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 管理者のパスワード（実行のたびの乱数）。 */
    protected final String adminPassword = TestDatabase.randomSecret();

    /**
     * 種類に合ったテストの DB を作る（まだ起動しない）。
     *
     * @param product DB の種類
     * @return テストの DB
     */
    public static TargetDbTestDatabase of(DatabaseProduct product) {
        return switch (product) {
            case MYSQL, MARIADB -> new MysqlFamilyTestDatabase(product);
            case POSTGRESQL -> new PostgresTestDatabase();
        };
    }

    /**
     * 名前の重ならない名前（接頭辞＋16進数8桁）を作る。
     *
     * @param prefix 接頭辞（英数字と {@code _}）
     * @return 名前
     */
    public static String uniqueName(String prefix) {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return prefix + HexFormat.of().formatHex(bytes);
    }

    /**
     * DB の種類を返す。
     *
     * @return 種類
     */
    public abstract DatabaseProduct product();

    /**
     * コンテナを返す。
     *
     * @return コンテナ
     */
    protected abstract JdbcDatabaseContainer<?> container();

    /** コンテナを起動する。 */
    public void start() {
        container().start();
    }

    /** コンテナを止める（「止めた DB」を作るときにも使う）。 */
    public void stop() {
        container().stop();
    }

    /**
     * アプリから接続するホストを返す。
     *
     * @return ホスト
     */
    public String host() {
        return container().getHost();
    }

    /**
     * アプリから接続する番号を返す。
     *
     * @return 番号
     */
    public abstract int port();

    /**
     * アプリの設定の {@code database} の項目に入れる値を返す。
     *
     * @param schema 読むスキーマ
     * @return DB の名前（MySQL・MariaDB はスキーマと同じ）
     */
    public abstract String connectDatabase(String schema);

    /**
     * 管理者で接続する。
     *
     * @return 接続
     * @throws SQLException 接続の失敗
     */
    public Connection admin() throws SQLException {
        return DriverManager.getConnection(container().getJdbcUrl(), adminUser(), adminPassword);
    }

    /**
     * 管理者のユーザー名を返す。
     *
     * @return ユーザー名
     */
    protected abstract String adminUser();

    /**
     * 指定したアカウントで、スキーマを読むための接続を作る。
     *
     * @param schema スキーマ
     * @param user ユーザー名
     * @param password パスワード
     * @return 接続
     * @throws SQLException 接続の失敗
     */
    public abstract Connection connect(String schema, String user, String password) throws SQLException;

    /**
     * 識別子を種類に合った引用符で囲む（テストの DDL を組み立てるため）。
     *
     * @param identifier 識別子
     * @return 囲んだ識別子
     */
    public String q(String identifier) {
        return SqlIdentifiers.quote(product(), identifier);
    }

    /**
     * 管理者で SQL を順に実行する。
     *
     * @param sql SQL
     */
    public void execute(String... sql) {
        try (Connection connection = admin();
                Statement statement = connection.createStatement()) {
            for (String each : sql) {
                statement.execute(each);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("テストの準備の SQL に失敗しました", e);
        }
    }

    /**
     * 空のスキーマを作る。
     *
     * @param schema スキーマ
     */
    public abstract void createSchema(String schema);

    /**
     * スキーマを中のものごと消す。
     *
     * @param schema スキーマ
     */
    public abstract void dropSchema(String schema);

    /**
     * スキーマの読み取りの権限だけを持つアカウントを作る（スキーマの表・ビューを作った後に呼ぶ）。
     *
     * @param user ユーザー名
     * @param password パスワード
     * @param schema スキーマ
     */
    public abstract void createReadOnlyAccount(String user, String password, String schema);

    /**
     * アカウントを消す。
     *
     * @param user ユーザー名
     */
    public abstract void dropAccount(String user);

    /**
     * 標準のテストの表・ビュー・外部キー・コメント・記号を含む名前を作る（{@link TargetDbFixture}）。
     *
     * @param schema 読むスキーマ
     * @param otherSchema 別のスキーマ（外部キーの参照先の表を置く）
     */
    public abstract void createStandardFixture(String schema, String otherSchema);
}
