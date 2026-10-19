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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnKind;
import cherry.mastersmith.businessdata.domain.KnownIdentifiers;
import cherry.mastersmith.businessdata.domain.RowKey;
import cherry.mastersmith.businessdata.domain.RowWriteFailureReason;
import cherry.mastersmith.businessdata.domain.RowWriteStage;
import cherry.mastersmith.businessdata.domain.TableAccessInput;
import cherry.mastersmith.businessdata.domain.TableAccessPolicy;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.TypedValue;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetWriteSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 行の SQL の部品の書き込みのメソッドのモックの JDBC での単体テスト（Intent 261016-business-data-views の U2、BR5〜BR8・BR10、
 * 試しの W8、NFR 設計のレビュー R-02）。
 */
class RowSqlExecutorWriteTest {

    private static final DslTable ITEM = BusinessDataDslFixture.tableWithForeignKeys(
            "item",
            List.of("id"),
            List.of(new DslForeignKey(List.of("maker"), "maker", List.of("id"))),
            column("id").dbType("int4").build(),
            column("code")
                    .validation(ValidationType.UNIQUE, ValidationOrigin.MANUAL)
                    .build(),
            column("maker").dbType("int4").build(),
            column("note").build());

    private static final TableAccessView VIEW = view(ITEM);

    private static final RowKey KEY = new RowKey(Map.of("id", new BoundValue(ValueType.INTEGER, 7L)));

    private final RowSqlExecutor executor = new RowSqlExecutor();

    private final List<String> calls = new ArrayList<>();

    private Connection connection;

    private PreparedStatement statement;

    private ResultSet resultSet;

    private static TableAccessView view(DslTable table) {
        Map<String, MainPermission> main = new HashMap<>();
        Map<String, ColumnKind> kinds = new HashMap<>();
        table.columns().keySet().forEach(name -> {
            main.put(name, MainPermission.FULL);
            kinds.put(name, new ColumnKind(ValueType.STRING, true));
        });
        kinds.put("id", new ColumnKind(ValueType.INTEGER, true));
        kinds.put("maker", new ColumnKind(ValueType.INTEGER, true));
        return TableAccessPolicy.evaluate(new TableAccessInput(
                "sample",
                table,
                BusinessDataDslFixture.DEFINITION,
                MainPermission.FULL,
                true,
                true,
                main,
                kinds,
                true,
                new KnownIdentifiers(
                        Set.of("sample"),
                        Set.of("item", "maker"),
                        table.columns().keySet()),
                Map.of("maker", Set.of("id"))));
    }

    @BeforeEach
    void setUp() throws SQLException {
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            calls.add("prepare");
            return statement;
        });
        when(connection.prepareStatement(anyString(), any(String[].class))).thenAnswer(invocation -> {
            calls.add("prepareWithKeys");
            return statement;
        });
        when(statement.executeQuery()).thenReturn(resultSet);
        when(statement.getGeneratedKeys()).thenReturn(resultSet);
    }

    private TargetWriteSession session(DatabaseProduct product) {
        return new TargetWriteSession(product, connection, 5, () -> calls.add("savepoint"));
    }

    private static TypedValue text(String column, String value) {
        return new TypedValue(column, new BoundValue(ValueType.STRING, value));
    }

    private static TypedValue number(String column, long value) {
        return new TypedValue(column, new BoundValue(ValueType.INTEGER, value));
    }

    @Test
    @DisplayName("PostgreSQL creates a row after a savepoint and reads only the generated key columns by name")
    void insertReadsGeneratedKeysByName() throws SQLException {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.findColumn("id")).thenReturn(1);
        when(resultSet.getLong(1)).thenReturn(101L);

        RowInsertResult result = executor.insert(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("code", "A")));

        assertThat(result)
                .isInstanceOfSatisfying(
                        RowInsertResult.Inserted.class,
                        inserted -> assertThat(inserted.key().values()).containsExactly(Map.entry("id", "101")));
        assertThat(calls).containsExactly("savepoint", "prepareWithKeys");
        verify(connection)
                .prepareStatement("INSERT INTO \"sample\".\"item\" (\"code\") VALUES (?)", new String[] {"id"});
        verify(statement).setQueryTimeout(5);
        verify(statement).setString(1, "A");
        assertThat(result.toString()).doesNotContain("101");
    }

    @Test
    @DisplayName("a primary key sent as a value is returned as it is without reading generated keys")
    void insertWithSentKey() throws SQLException {
        RowInsertResult result =
                executor.insert(session(DatabaseProduct.MYSQL), VIEW, List.of(number("id", 9L), text("code", "A")));

        assertThat(((RowInsertResult.Inserted) result).key().values()).containsExactly(Map.entry("id", "9"));
        assertThat(calls).containsExactly("savepoint", "prepare");
        verify(statement, never()).getGeneratedKeys();
    }

    @Test
    @DisplayName("MySQL reads the single generated key by position and a table without values uses the default form")
    void insertMysqlGeneratedKeyAndDefaults() throws SQLException {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(5L);

        RowInsertResult result = executor.insert(session(DatabaseProduct.MARIADB), VIEW, List.of());

        assertThat(((RowInsertResult.Inserted) result).key().values()).containsExactly(Map.entry("id", "5"));
        verify(connection).prepareStatement("INSERT INTO `sample`.`item` () VALUES ()", new String[] {"id"});
        verify(resultSet, never()).findColumn(anyString());
    }

    @Test
    @DisplayName("MySQL cannot read two missing key columns, so the created key is empty and nothing fails")
    void insertMysqlCompositeKeyUnknown() throws SQLException {
        DslTable pair = BusinessDataDslFixture.tableWithForeignKeys(
                "item",
                List.of("id", "note"),
                List.of(),
                column("id").dbType("int4").build(),
                column("note").build());

        RowInsertResult result = executor.insert(session(DatabaseProduct.MYSQL), view(pair), List.of());

        assertThat(((RowInsertResult.Inserted) result).key().values()).isEmpty();
        assertThat(calls).containsExactly("savepoint", "prepare");
    }

    @Test
    @DisplayName("a generated key that cannot be read as text leaves the created key empty instead of failing")
    void insertGeneratedKeyNotText() throws SQLException {
        // コードのレビューの R-03: 文字として読めない鍵（ここでは NULL）は、作成を失敗にせず、鍵を返さない。
        when(resultSet.next()).thenReturn(true);
        when(resultSet.findColumn("id")).thenReturn(1);
        when(resultSet.getLong(1)).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(true);

        RowInsertResult result = executor.insert(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("code", "A")));

        assertThat(result)
                .isInstanceOfSatisfying(
                        RowInsertResult.Inserted.class,
                        inserted -> assertThat(inserted.key().values()).isEmpty());
        assertThat(calls).containsExactly("savepoint", "prepareWithKeys");
    }

    @Test
    @DisplayName("a failure while reading the generated key is an unexpected failure of the result stage")
    void insertResultReadFailure() throws SQLException {
        when(statement.getGeneratedKeys()).thenThrow(new SQLException("値 101", "22003", 0));

        RowInsertResult result = executor.insert(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("code", "A")));

        assertThat(((RowInsertResult.Failed) result).failure()).satisfies(f -> {
            assertThat(f.reason()).isEqualTo(RowWriteFailureReason.UNEXPECTED);
            assertThat(f.stage()).isEqualTo(RowWriteStage.READ_RESULT);
        });
    }

    @Test
    @DisplayName("constraint violations of the create statement are classified at the write stage")
    void insertViolation() throws SQLException {
        when(statement.executeUpdate()).thenThrow(new SQLException("値 A は重なる", "23505", 0));

        RowInsertResult result = executor.insert(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("code", "A")));

        assertThat(((RowInsertResult.Failed) result).failure()).satisfies(f -> {
            assertThat(f.reason()).isEqualTo(RowWriteFailureReason.DUPLICATE);
            assertThat(f.stage()).isEqualTo(RowWriteStage.WRITE);
            assertThat(f.toString()).doesNotContain("値 A");
        });
    }

    @Test
    @DisplayName("a savepoint that cannot be set fails before the write statement is sent")
    void savepointFailure() throws SQLException {
        TargetWriteSession failing = new TargetWriteSession(DatabaseProduct.POSTGRESQL, connection, 5, () -> {
            throw new SQLException("x", "08006", 0);
        });

        RowChangeResult result = executor.update(failing, VIEW, List.of(text("code", "A")), KEY);

        assertThat(((RowChangeResult.Failed) result).failure()).satisfies(f -> {
            assertThat(f.reason()).isEqualTo(RowWriteFailureReason.UNAVAILABLE);
            assertThat(f.stage()).isEqualTo(RowWriteStage.PRECHECK);
        });
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    @DisplayName("update and delete return the matched row count and the delete marks no savepoint")
    void updateAndDeleteCount() throws SQLException {
        when(statement.executeLargeUpdate()).thenReturn(1L, 2L);

        assertThat(executor.update(session(DatabaseProduct.MYSQL), VIEW, List.of(text("note", null)), KEY))
                .isEqualTo(new RowChangeResult.Changed(1));
        assertThat(executor.delete(session(DatabaseProduct.MYSQL), VIEW, KEY))
                .isEqualTo(new RowChangeResult.Changed(2));
        assertThat(calls).containsExactly("savepoint", "prepare", "prepare");
        verify(statement).setNull(1, java.sql.Types.VARCHAR);
    }

    @Test
    @DisplayName("a referenced row that blocks a delete is a rejected save, not a missing reference")
    void deleteForeignKey() throws SQLException {
        when(statement.executeLargeUpdate()).thenThrow(new SQLException("x", "23503", 0));

        RowChangeResult result = executor.delete(session(DatabaseProduct.POSTGRESQL), VIEW, KEY);

        assertThat(((RowChangeResult.Failed) result).failure().reason()).isEqualTo(RowWriteFailureReason.REJECTED);
    }

    @Test
    @DisplayName("the precheck runs no statement without unique values and reports the overlapping columns")
    void precheck() throws SQLException {
        assertThat(executor.precheckUnique(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("note", "x")), null))
                .isInstanceOf(RowPrecheckResult.Clear.class);
        verify(connection, never()).prepareStatement(anyString());

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(1)).thenReturn(1);
        when(resultSet.wasNull()).thenReturn(false);
        assertThat(executor.precheckUnique(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("code", "A")), KEY))
                .isEqualTo(new RowPrecheckResult.Duplicate(List.of("code")));

        when(statement.executeQuery()).thenThrow(new SQLTimeoutException());
        assertThat(((RowPrecheckResult.Failed) executor.precheckUnique(
                                session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("code", "A")), null))
                        .failure()
                        .reason())
                .as("書き込みの文を送る前の時間切れ")
                .isEqualTo(RowWriteFailureReason.UNAVAILABLE);
    }

    @Test
    @DisplayName("the inspection names the single candidate column and an empty field for none")
    void inspection() throws SQLException {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(1)).thenReturn(null);
        when(resultSet.wasNull()).thenReturn(true);

        assertThat(executor.inspectViolation(
                        session(DatabaseProduct.POSTGRESQL), VIEW, true, List.of(number("maker", 3L)), null))
                .as("参照先の行が無い外部キーが候補")
                .isEqualTo(new RowInspectResult.Inspected(Optional.of("maker")));
        assertThat(executor.inspectViolation(
                        session(DatabaseProduct.POSTGRESQL), VIEW, false, List.of(text("code", "A")), null))
                .as("ほかの行が無い一意のカラムは候補にしない")
                .isEqualTo(new RowInspectResult.Inspected(Optional.empty()));
        assertThat(executor.inspectViolation(
                        session(DatabaseProduct.POSTGRESQL), VIEW, true, List.of(text("note", "x")), null))
                .isEqualTo(new RowInspectResult.Inspected(Optional.empty()));
        InOrder order = inOrder(connection);
        order.verify(connection)
                .prepareStatement("SELECT (SELECT 1 FROM \"sample\".\"maker\" WHERE \"id\" = ? LIMIT 1) AS c0");
    }

    @Test
    @DisplayName("failures of the inspection and unchecked failures while binding become results")
    void inspectionAndBindFailures() throws SQLException {
        when(statement.executeQuery()).thenThrow(new SQLException("値", "42000", 1064));
        assertThat(((RowInspectResult.Failed) executor.inspectViolation(
                                session(DatabaseProduct.MYSQL), VIEW, false, List.of(text("code", "A")), KEY))
                        .failure())
                .satisfies(f -> {
                    assertThat(f.reason()).isEqualTo(RowWriteFailureReason.REJECTED);
                    assertThat(f.stage()).isEqualTo(RowWriteStage.INSPECT);
                });

        doThrow(new NumberFormatException("値 1x")).when(statement).setString(anyInt(), anyString());
        RowInsertResult bind = executor.insert(session(DatabaseProduct.POSTGRESQL), VIEW, List.of(text("code", "A")));
        assertThat(((RowInsertResult.Failed) bind).failure()).satisfies(f -> {
            assertThat(f.reason()).isEqualTo(RowWriteFailureReason.UNEXPECTED);
            assertThat(f.exceptionClass()).isEqualTo("java.lang.NumberFormatException");
        });
    }
}
