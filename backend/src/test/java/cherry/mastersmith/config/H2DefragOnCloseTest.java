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
package cherry.mastersmith.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 内部DB（組み込みの H2）の終了時の詰め直しの確かめ（U4-STORAGE、Build and Test からの戻し Loop-back 1）。
 *
 * <p>DSL の本文（最大 10MB）の履歴は、上限を超えた古い行を消しても、動いている間は H2 のファイルの空いた場所が埋め直されず、
 * 閉じるときに詰め直さないとファイルが縮まない（起動し直しても縮まない）。既定の接続先に {@code DEFRAG_ALWAYS=TRUE} を付けると、
 * 最後の接続を閉じて DB が閉じるときに詰め直しが自動で行われる。
 *
 * <p>ここでは、アプリが動いている間と同じく1本の接続を開いたまま、履歴の表と同じ {@code BINARY LARGE OBJECT} の列に大きな本文を
 * 足しては古い行を消す（最新の数行だけを残す）ことを繰り返し、閉じた後と、開き直して閉じた後のファイルの大きさを、付けたときと
 * 付けないときで比べる。あわせて、{@code application.yaml} の既定の接続先に付いていることを確かめる。
 */
class H2DefragOnCloseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2DefragOnCloseTest.class);

    private static final String DEFRAG_ALWAYS = ";DEFRAG_ALWAYS=TRUE";

    private static final long MB = 1024L * 1024;

    /** 1行の本文の大きさ（1MB）。 */
    private static final int ROW_BYTES = (int) MB;

    /** 足す行の数（合わせて 24MB。数秒の内に終わる大きさ）。 */
    private static final int ROWS = 24;

    /** 残す最新の行の数（履歴の上限に当たる）。残る本文は 2MB。 */
    private static final int KEPT_ROWS = 2;

    /** 残る本文の大きさ（2MB）。 */
    private static final long LIVE_BYTES = MB * KEPT_ROWS;

    @TempDir
    Path tempDir;

    /**
     * 閉じた後と、開き直して閉じた後のファイルの大きさ。
     *
     * @param closed 行を足しては消すことを繰り返して閉じた後の大きさ
     * @param reopened もう一度開いて閉じた後の大きさ（起動し直しに当たる）
     */
    private record Sizes(long closed, long reopened) {}

    private Sizes addAndPruneHistory(String name, String settings) throws SQLException, IOException {
        Path base = tempDir.resolve(name).resolve("mastersmith");
        String url = "jdbc:h2:file:" + base.toAbsolutePath() + settings;
        Path file = base.resolveSibling("mastersmith.mv.db");

        // 本文は圧縮で小さくならないよう乱数のバイト列にする（種は固定し、実行ごとに同じ内容にする）。
        byte[] body = new byte[ROW_BYTES];
        new Random(20260924L).nextBytes(body);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE history (id INT PRIMARY KEY, yaml_bytes BINARY LARGE OBJECT NOT NULL)");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO history VALUES (?, ?)");
                    PreparedStatement prune = connection.prepareStatement("DELETE FROM history WHERE id <= ?")) {
                for (int id = 1; id <= ROWS; id++) {
                    insert.setInt(1, id);
                    insert.setBytes(2, body);
                    insert.executeUpdate();
                    prune.setInt(1, id - KEPT_ROWS);
                    prune.executeUpdate();
                }
            }
        }
        long closed = Files.size(file);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(connection.isValid(1)).isTrue();
        }
        long reopened = Files.size(file);

        LOGGER.atInfo()
                .addKeyValue("h2.settings", settings.isEmpty() ? "none" : settings)
                .addKeyValue("h2.closedBytes", closed)
                .addKeyValue("h2.reopenedBytes", reopened)
                .log("H2 のファイルの大きさ");
        return new Sizes(closed, reopened);
    }

    @Test
    @DisplayName("with DEFRAG_ALWAYS the H2 file shrinks to about the live data when the database is closed")
    void shrinksWithDefragAlways() throws SQLException, IOException {
        Sizes sizes = addAndPruneHistory("defrag", DEFRAG_ALWAYS);

        // 残る本文（2MB）と DB の枠だけになる。
        assertThat(sizes.closed()).isLessThan(LIVE_BYTES + MB);
        assertThat(sizes.reopened()).isLessThan(LIVE_BYTES + MB);
    }

    @Test
    @DisplayName("without DEFRAG_ALWAYS the H2 file keeps the space of the deleted rows even after it is reopened")
    void keepsDeletedSpaceWithoutDefragAlways() throws SQLException, IOException {
        Sizes sizes = addAndPruneHistory("plain", "");

        // 消した行の場所が残り、残る本文（2MB）の数倍の大きさのまま。開き直しても縮まない。
        assertThat(sizes.closed()).isGreaterThan(LIVE_BYTES * 4);
        assertThat(sizes.reopened()).isGreaterThanOrEqualTo(sizes.closed());
    }

    @Test
    @DisplayName("the default datasource URL in application.yaml sets DEFRAG_ALWAYS=TRUE")
    void defaultUrlSetsDefragAlways() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        assertThat(environment.getProperty("spring.datasource.url"))
                .startsWith("jdbc:h2:file:./data/mastersmith;")
                .containsIgnoringCase("DEFRAG_ALWAYS=TRUE");
    }
}
