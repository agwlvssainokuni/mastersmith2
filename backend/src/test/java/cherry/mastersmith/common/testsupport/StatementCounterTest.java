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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** {@link StatementCounter} の数え方の単体テスト（本物の H2 のメモリのデータベース）。 */
class StatementCounterTest {

    private static DriverManagerDataSource source() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl("jdbc:h2:mem:counter-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        source.setUsername("sa");
        return source;
    }

    @Test
    @DisplayName(
            "it counts executed prepared statements, including the generated-keys overload, and borrowed connections")
    void countsStatementsAndConnections() throws SQLException {
        StatementCounter counter = new StatementCounter();
        DataSource counted = counter.wrap(source());

        try (Connection connection = counted.getConnection()) {
            try (PreparedStatement create = connection.prepareStatement("CREATE TABLE t (id INT, v INT)")) {
                create.execute();
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO t VALUES (1, 1)")) {
                insert.executeUpdate();
            }
            try (PreparedStatement keyed =
                    connection.prepareStatement("UPDATE t SET v = 2 WHERE id = 1", new String[] {"id"})) {
                keyed.executeUpdate();
            }
            try (PreparedStatement query = connection.prepareStatement("SELECT v FROM t");
                    ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
            }
            // 準備しただけで流さない文は数えない。
            connection.prepareStatement("SELECT 1").close();
        }
        try (Connection second = counted.getConnection()) {
            assertThat(second.isValid(1)).isTrue();
        }

        assertThat(counter.statements()).isEqualTo(4);
        assertThat(counter.connections()).isEqualTo(2);

        counter.reset();
        assertThat(counter.statements()).isZero();
        assertThat(counter.connections()).isZero();
    }

    @Test
    @DisplayName("unwrap and isWrapperFor reach the original data source")
    void unwrapReachesTheOriginal() throws SQLException {
        DriverManagerDataSource original = source();
        DataSource counted = new StatementCounter().wrap(original);

        assertThat(counted.isWrapperFor(DriverManagerDataSource.class)).isTrue();
        assertThat(counted.unwrap(DriverManagerDataSource.class)).isSameAs(original);
    }

    @Test
    @DisplayName("only the bean named dataSource is wrapped and other beans are returned as they are")
    void wrapsOnlyTheInternalDataSourceBean() {
        StatementCounter counter = new StatementCounter();
        DriverManagerDataSource internal = source();
        DriverManagerDataSource target = source();
        Object other = new Object();

        assertThat(counter.postProcessAfterInitialization(target, "targetDataSource"))
                .isSameAs(target);
        assertThat(counter.postProcessAfterInitialization(other, "dataSource")).isSameAs(other);
        Object wrapped = counter.postProcessAfterInitialization(internal, StatementCounter.DATA_SOURCE_BEAN);
        assertThat(wrapped).isNotSameAs(internal).isInstanceOf(DataSource.class);
        assertThat(counter.getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }
}
