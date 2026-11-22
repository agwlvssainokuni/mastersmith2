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
import cherry.mastersmith.targetdb.testsupport.TargetDbImages;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 内部DB の PostgreSQL の結合テストが使うコンテナ（BR6.5）。JVM ごとに 1 つ起動して使い回し、JVM の終わりで止める。イメージは対象DB の
 * PostgreSQL と同じ版とダイジェスト（{@link TargetDbImages#postgres()}）。管理者のパスワードは起動ごとの乱数。
 */
public final class InternalDbPostgresContainer {

    private static final int PORT = 5432;

    private static final String ADMIN_PASSWORD = TestDatabase.randomSecret();

    private static PostgreSQLContainer container;

    private InternalDbPostgresContainer() {}

    /**
     * 起動済みのコンテナを返す（まだなら起動する）。
     *
     * @return コンテナ
     */
    public static synchronized PostgreSQLContainer get() {
        if (container == null) {
            PostgreSQLContainer started =
                    new PostgreSQLContainer(TargetDbImages.postgres()).withPassword(ADMIN_PASSWORD);
            started.start();
            Runtime.getRuntime().addShutdownHook(new Thread(started::stop));
            container = started;
        }
        return container;
    }

    /**
     * コンテナの管理者の接続（コンテナ既定のデータベースへ）。
     *
     * @return 接続
     * @throws SQLException 接続の失敗
     */
    static Connection adminConnection() throws SQLException {
        PostgreSQLContainer c = get();
        return DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), ADMIN_PASSWORD);
    }

    /**
     * 管理者のパスワード。
     *
     * @return パスワード（起動ごとの乱数）
     */
    static String adminPassword() {
        return ADMIN_PASSWORD;
    }

    /**
     * コンテナ内の PostgreSQL のポート（ホストへ写した番号）。
     *
     * @return ポート番号
     */
    public static int mappedPort() {
        return get().getMappedPort(PORT);
    }

    /** コンテナを一時停止する（届かない状態を作る）。 */
    public static void pause() {
        PostgreSQLContainer c = get();
        c.getDockerClient().pauseContainerCmd(c.getContainerId()).exec();
    }

    /** 一時停止したコンテナを再開する。 */
    public static void unpause() {
        PostgreSQLContainer c = get();
        c.getDockerClient().unpauseContainerCmd(c.getContainerId()).exec();
    }
}
