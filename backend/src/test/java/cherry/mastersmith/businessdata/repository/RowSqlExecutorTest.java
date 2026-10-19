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
package cherry.mastersmith.businessdata.repository;

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.kind;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnKind;
import cherry.mastersmith.businessdata.domain.PageRequest;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.RowQueryFailureReason;
import cherry.mastersmith.businessdata.domain.RowQueryStage;
import cherry.mastersmith.businessdata.domain.SearchCondition;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 行の SQL を組み立てて実行する唯一の部品（BR3.3〜BR3.5・BR5.3〜BR5.8・BR6.3、R-01〜R-03）のモックの JDBC での単体テスト。 */
class RowSqlExecutorTest {

    private static final DslTable ITEM = table(
            "item",
            false,
            List.of("code"),
            column("code").dbType("int4").list(true, 1).build(),
            column("name").list(true, 2).search(SearchOperator.CONTAINS).build(),
            column("price")
                    .dbType("numeric")
                    .list(true, 3)
                    .search(SearchOperator.RANGE)
                    .defaultSort(SortDirection.DESC)
                    .build(),
            column("pw").form(FormPart.PASSWORD).list(true, 4).build(),
            column("photo").dbType("bytea").list(true, 5).build(),
            column("memo").list(false, null).build());

    private static final Map<String, ColumnKind> KINDS =
            Map.of("code", kind(ValueType.INTEGER), "price", kind(ValueType.DECIMAL), "photo", kind(ValueType.BINARY));

    private static final TableAccessView VIEW = BusinessDataDslFixture.view(ITEM, MainPermission.READ, KINDS);

    private static final List<SearchCondition> CONDITIONS = List.of(
            SearchCondition.contains("name", new BoundValue(ValueType.STRING, "50%_!")),
            SearchCondition.range("price", ValueType.DECIMAL, new BoundValue(ValueType.DECIMAL, BigDecimal.TEN), null));

    private final RowSqlExecutor executor = new RowSqlExecutor();

    private Connection connection;

    private PreparedStatement first;

    private PreparedStatement second;

    private ResultSet firstResult;

    private ResultSet secondResult;

    private Statement init;

    @BeforeEach
    void setUp() throws SQLException {
        connection = mock(Connection.class);
        first = mock(PreparedStatement.class);
        second = mock(PreparedStatement.class);
        firstResult = mock(ResultSet.class);
        secondResult = mock(ResultSet.class);
        init = mock(Statement.class);
        when(connection.prepareStatement(anyString())).thenReturn(first, second);
        when(connection.createStatement()).thenReturn(init);
        when(first.executeQuery()).thenReturn(firstResult);
        when(second.executeQuery()).thenReturn(secondResult);
    }

    private TargetReadSession session(DatabaseProduct product) {
        return new TargetReadSession(product, connection, 5);
    }

    @Test
    @DisplayName("PostgreSQL reads the count and then the page, joining on the primary key read first")
    void postgresSearchShape() throws SQLException {
        when(firstResult.next()).thenReturn(true);
        when(firstResult.getLong(1)).thenReturn(25L);
        when(secondResult.next()).thenReturn(true, false);
        when(secondResult.getLong(anyInt())).thenReturn(7L);
        when(secondResult.getString(3)).thenReturn("ペン");
        when(secondResult.getBigDecimal(4)).thenReturn(new BigDecimal("1.50"));

        RowSearchResult result =
                executor.search(session(DatabaseProduct.POSTGRESQL), VIEW, CONDITIONS, new PageRequest(2, 20, null));

        InOrder order = inOrder(connection, first, second);
        order.verify(connection)
                .prepareStatement("SELECT COUNT(*) FROM \"sample\".\"item\""
                        + " WHERE \"name\" LIKE ? ESCAPE '!' AND \"price\" >= ?");
        order.verify(connection)
                .prepareStatement(
                        "SELECT t.\"code\", t.\"code\", SUBSTRING(t.\"name\", 1, 101), t.\"price\""
                                + " FROM \"sample\".\"item\" t JOIN (SELECT \"code\" FROM \"sample\".\"item\""
                                + " WHERE \"name\" LIKE ? ESCAPE '!' AND \"price\" >= ?"
                                + " ORDER BY \"price\" DESC NULLS FIRST, \"code\" ASC NULLS LAST LIMIT ? OFFSET ?) p"
                                + " ON t.\"code\" = p.\"code\" ORDER BY t.\"price\" DESC NULLS FIRST, t.\"code\" ASC NULLS LAST");
        verify(connection, never()).createStatement();
        verify(first).setQueryTimeout(5);
        verify(first).setMaxRows(1);
        verify(first).setString(1, "%50!%!_!!%");
        verify(first).setBigDecimal(2, BigDecimal.TEN);
        verify(second).setQueryTimeout(5);
        verify(second).setMaxRows(20);
        verify(second).setString(1, "%50!%!_!!%");
        verify(second).setBigDecimal(2, BigDecimal.TEN);
        verify(second).setLong(3, 20L);
        verify(second).setLong(4, 20L);
        assertThat(result).isInstanceOfSatisfying(RowSearchResult.Page.class, page -> {
            assertThat(page.total()).isEqualTo(25L);
            assertThat(page.rows()).singleElement().satisfies(row -> {
                assertThat(row.key().values()).containsExactly(Map.entry("code", "7"));
                assertThat(row.values().values())
                        .as("password と BINARY は値を持たない")
                        .containsOnlyKeys("code", "name", "price")
                        .containsEntry("name", "ペン")
                        .containsEntry("price", "1.50");
            });
        });
    }

    @Test
    @DisplayName("MySQL runs no session statement on lending and uses backticks and IS NULL ordering")
    void mysqlSearchShape() throws SQLException {
        when(firstResult.next()).thenReturn(true);
        when(firstResult.getLong(1)).thenReturn(1L);
        when(secondResult.next()).thenReturn(false);

        executor.search(session(DatabaseProduct.MYSQL), VIEW, List.of(), new PageRequest(1, 50, null));

        // タイムゾーンは接続を作るときの初期化の文で付けるため、貸し出しの始めの文は流さない（U2 の D5: A）。
        verify(connection, never()).createStatement();
        InOrder order = inOrder(connection);
        order.verify(connection).prepareStatement("SELECT COUNT(*) FROM `sample`.`item`");
        order.verify(connection)
                .prepareStatement("SELECT t.`code`, t.`code`, SUBSTRING(t.`name`, 1, 101), t.`price`"
                        + " FROM `sample`.`item` t JOIN (SELECT `code` FROM `sample`.`item`"
                        + " ORDER BY `price` IS NULL DESC, `price` DESC, `code` IS NULL ASC, `code` ASC LIMIT ? OFFSET ?) p"
                        + " ON t.`code` = p.`code` ORDER BY t.`price` IS NULL DESC, t.`price` DESC,"
                        + " t.`code` IS NULL ASC, t.`code` ASC");
        verify(second).setLong(1, 50L);
        verify(second).setLong(2, 0L);
    }

    @Test
    @DisplayName("a page at or beyond the total returns the count without preparing a second statement")
    void beyondTheLastPage() throws SQLException {
        when(firstResult.next()).thenReturn(true);
        when(firstResult.getLong(1)).thenReturn(40L);

        RowSearchResult result =
                executor.search(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(), new PageRequest(3, 20, null));

        assertThat(result).isEqualTo(new RowSearchResult.Page(40L, List.of()));
        verify(connection, times(1)).prepareStatement(anyString());
    }

    @Test
    @DisplayName("a table without a primary key is read as it is, with no key and no order")
    void tableWithoutPrimaryKey() throws SQLException {
        DslTable log =
                table("log", false, List.of(), column("text").list(true, 1).build());
        TableAccessView view = BusinessDataDslFixture.view(log, MainPermission.READ, Map.of());
        when(firstResult.next()).thenReturn(true);
        when(firstResult.getLong(1)).thenReturn(1L);
        when(secondResult.next()).thenReturn(true, false);
        when(secondResult.getString(1)).thenReturn("x".repeat(101));

        RowSearchResult result =
                executor.search(session(DatabaseProduct.POSTGRESQL), view, List.of(), new PageRequest(1, 20, null));

        verify(connection)
                .prepareStatement("SELECT SUBSTRING(\"text\", 1, 101) FROM \"sample\".\"log\" LIMIT ? OFFSET ?");
        assertThat(((RowSearchResult.Page) result).rows()).singleElement().satisfies(row -> {
            assertThat(row.key()).isNull();
            assertThat(row.values().values().get("text")).hasSize(100);
            assertThat(row.values().truncatedColumns()).containsExactly("text");
        });
    }

    @Test
    @DisplayName("a name that is not in the known identifiers prepares no statement and is unexpected")
    void unknownIdentifier() throws SQLException {
        List<SearchCondition> forged =
                List.of(SearchCondition.equalTo("name\"; DROP TABLE item; --", new BoundValue(ValueType.STRING, "x")));

        RowSearchResult result =
                executor.search(session(DatabaseProduct.MYSQL), VIEW, forged, new PageRequest(1, 20, null));

        assertThat(result).isInstanceOfSatisfying(RowSearchResult.Failed.class, failed -> {
            assertThat(failed.failure().reason()).isEqualTo(RowQueryFailureReason.UNEXPECTED);
            assertThat(failed.failure().stage()).isEqualTo(RowQueryStage.BIND);
        });
        verify(connection, never()).prepareStatement(anyString());
        verify(connection, never()).createStatement();
    }

    @Test
    @DisplayName("a timeout of the count fails the whole request and no further statement is prepared")
    void countTimeout() throws SQLException {
        when(first.executeQuery()).thenThrow(new SQLTimeoutException("cancel ペン", "57014"));

        RowSearchResult result =
                executor.search(session(DatabaseProduct.POSTGRESQL), VIEW, CONDITIONS, new PageRequest(1, 20, null));

        assertThat(result).isInstanceOfSatisfying(RowSearchResult.Failed.class, failed -> {
            assertThat(failed.failure().reason()).isEqualTo(RowQueryFailureReason.TIMEOUT);
            assertThat(failed.failure().stage()).isEqualTo(RowQueryStage.EXECUTE);
            assertThat(failed.toString()).doesNotContain("ペン");
        });
        verify(connection, times(1)).prepareStatement(anyString());
    }

    @Test
    @DisplayName("unchecked failures while binding become results")
    void sessionAndBindFailures() throws SQLException {
        connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(first);
        org.mockito.Mockito.doThrow(new IllegalStateException("bad"))
                .when(first)
                .setString(anyInt(), anyString());
        RowSearchResult bind =
                executor.search(session(DatabaseProduct.POSTGRESQL), VIEW, CONDITIONS, new PageRequest(1, 20, null));
        assertThat(((RowSearchResult.Failed) bind).failure()).satisfies(f -> {
            assertThat(f.reason()).isEqualTo(RowQueryFailureReason.UNEXPECTED);
            assertThat(f.exceptionClass()).isEqualTo("java.lang.IllegalStateException");
        });
    }

    @Test
    @DisplayName("the detail reads at most two rows by the typed key and tells 0, 1 and 2 rows apart")
    void detail() throws SQLException {
        RowKey key = new RowKey(Map.of("code", new BoundValue(ValueType.INTEGER, 7L)));
        when(firstResult.next()).thenReturn(false);

        RowDetailResult none = executor.detail(session(DatabaseProduct.POSTGRESQL), VIEW, key);

        verify(connection)
                .prepareStatement("SELECT \"code\", \"code\", SUBSTRING(\"name\", 1, 10001), \"price\","
                        + " SUBSTRING(\"memo\", 1, 10001) FROM \"sample\".\"item\" WHERE \"code\" = ? LIMIT 2");
        verify(first).setMaxRows(2);
        verify(first).setLong(1, 7L);
        assertThat(none).isEqualTo(new RowDetailResult.NotFound());

        when(secondResult.next()).thenReturn(true, true);
        assertThat(executor.detail(session(DatabaseProduct.POSTGRESQL), VIEW, key))
                .isEqualTo(new RowDetailResult.NotUnique());
    }

    @Test
    @DisplayName("a value that cannot be read as the DSL type fails the detail as READ_CONVERSION, not as 400")
    void unreadableValue() throws SQLException {
        RowKey key = new RowKey(Map.of("code", new BoundValue(ValueType.INTEGER, 7L)));
        when(firstResult.next()).thenReturn(true);
        when(firstResult.getLong(anyInt())).thenReturn(7L);
        when(firstResult.getBigDecimal(4)).thenThrow(new NumberFormatException("Character N is neither a decimal"));

        RowDetailResult result = executor.detail(session(DatabaseProduct.MYSQL), VIEW, key);

        assertThat(result).isInstanceOfSatisfying(RowDetailResult.Failed.class, failed -> {
            assertThat(failed.failure().reason()).isEqualTo(RowQueryFailureReason.READ_CONVERSION);
            assertThat(failed.failure().stage()).isEqualTo(RowQueryStage.READ);
            assertThat(failed.failure().exceptionClass()).isEqualTo("java.lang.NumberFormatException");
        });
    }

    @Test
    @DisplayName("one row of the detail returns the full key and the detail columns")
    void detailFound() throws SQLException {
        RowKey key = new RowKey(Map.of("code", new BoundValue(ValueType.INTEGER, 7L)));
        when(firstResult.next()).thenReturn(true, false);
        when(firstResult.getLong(anyInt())).thenReturn(7L);
        when(firstResult.getString(5)).thenReturn(null);

        RowDetailResult result = executor.detail(session(DatabaseProduct.POSTGRESQL), VIEW, key);

        assertThat(result).isInstanceOfSatisfying(RowDetailResult.Found.class, found -> {
            assertThat(found.row().key().values()).containsEntry("code", "7");
            assertThat(found.row().values().values())
                    .containsKeys("code", "name", "price", "memo")
                    .containsEntry("memo", null);
        });
    }
}
