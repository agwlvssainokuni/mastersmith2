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
package cherry.mastersmith.appearance.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * 内部DB の接続プールから接続を借りた回数を数えるテストの補助（U8 の NFR5.2、NFR 設計の承認の場の U8 R-02）。
 *
 * <p>コード生成の Step 3 の判定で、テストの Spring の文脈の {@link MeterRegistry} に、名前 {@value #METER}・タグ
 * {@code pool=}{@value #POOL} のタイマーがあり（判定の (a)）、{@link DataSource#getConnection()} を1回呼んで閉じると件数が
 * 増える（判定の (b)）ことを確かめたため、HikariCP の指標の件数で数える。本番のコードは変えない。
 *
 * <p>「増えない」を確かめるテストは、先に {@link #assertCountingWorks()} で数え方が働くことを確かめ、空振りの確かめにしない。
 */
public final class ConnectionAcquireCounter {

    /** 接続を借りるたびに記録される HikariCP のタイマーの名前。 */
    public static final String METER = "hikaricp.connections.acquire";

    /** 内部DB の接続プールの名前（{@code spring.datasource.hikari.pool-name}）。 */
    public static final String POOL = "mastersmith-db";

    private final MeterRegistry registry;

    private final DataSource dataSource;

    /**
     * 作る。
     *
     * @param registry テストの Spring の文脈の指標の置き場
     * @param dataSource 内部DB の接続の元
     */
    public ConnectionAcquireCounter(MeterRegistry registry, DataSource dataSource) {
        this.registry = registry;
        this.dataSource = dataSource;
    }

    /**
     * これまでに接続を借りた回数を返す。
     *
     * @return 回数
     * @throws IllegalStateException タイマーが見つからないとき（数えられない）
     */
    public long count() {
        Timer timer = registry.find(METER).tag("pool", POOL).timer();
        if (timer == null) {
            throw new IllegalStateException("接続を借りた回数のタイマーが見つからない: " + METER + " pool=" + POOL);
        }
        return timer.count();
    }

    /**
     * 接続を1回借りて返したときに回数が増えることを確かめる（数え方が働いていることの確かめ）。
     *
     * @throws SQLException 接続を借りられなかったとき
     */
    public void assertCountingWorks() throws SQLException {
        long before = count();
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
        assertThat(count()).as("接続を1回借りると回数が増える").isGreaterThan(before);
    }
}
