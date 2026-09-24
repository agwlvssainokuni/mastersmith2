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
package cherry.mastersmith.targetdb.repository;

import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER_COMMENT;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER_ID;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER_VIEW;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.FK_CUSTOMER;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.NAME_COMMENT;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.ORDER_ITEMS;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.OTHER_TABLE;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.SYMBOL_COLUMN;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.SYMBOL_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetForeignKey;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetTable;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 情報スキーマの読み手の結合テストの共通の確かめ（BR2.1〜BR2.7、AC1.1.1・AC1.1.9・AC1.1.10・AC1.2.1、NFR6.2・NFR6.4、
 * NFR12.1・NFR12.2）。3種類の DB の子のクラスで、同じ内容を確かめる。
 *
 * <p>テストのクラスごとに本物の DB をコンテナで1つ起動し、名前の重ならないスキーマ（標準・別・空）と読み取りの権限だけの
 * アカウントを作り、クラスの終わりに消す。読み取りは、特に断りが無ければ読み取りの権限だけのアカウントで行う。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
abstract class AbstractSchemaQueriesIT {

    /** 問い合わせ1回の待ちの上限（秒。既定の DSL の生成と同じ値）。 */
    private static final int QUERY_TIMEOUT_SECONDS = 20;

    private final TargetDbTestDatabase database = TargetDbTestDatabase.of(product());

    private final String schema = TargetDbTestDatabase.uniqueName("Sales_");

    private final String otherSchema = TargetDbTestDatabase.uniqueName("other_");

    private final String emptySchema = TargetDbTestDatabase.uniqueName("empty_");

    private final String reader = TargetDbTestDatabase.uniqueName("reader_");

    private final String readerPassword = TestDatabase.randomSecret();

    /**
     * 確かめる DB の種類を返す。
     *
     * @return 種類
     */
    abstract DatabaseProduct product();

    /** 整数の型の名前（MySQL・MariaDB は int、PostgreSQL は int4）。 */
    abstract String expectedIntegerType();

    /** 固定小数の型の名前（MySQL・MariaDB は decimal、PostgreSQL は numeric）。 */
    abstract String expectedDecimalType();

    /** 数の既定値 0 を DB が返す書き方。 */
    abstract String expectedAmountDefault();

    /** 文字列の既定値 new を DB が返す書き方。 */
    abstract String expectedStatusDefault();

    @BeforeAll
    void startDatabase() {
        database.start();
        database.createSchema(otherSchema);
        database.createSchema(schema);
        database.createSchema(emptySchema);
        database.createStandardFixture(schema, otherSchema);
        database.createReadOnlyAccount(reader, readerPassword, schema);
    }

    @AfterAll
    void dropAndStop() {
        try {
            database.dropAccount(reader);
            database.dropSchema(schema);
            database.dropSchema(emptySchema);
            database.dropSchema(otherSchema);
        } finally {
            database.stop();
        }
    }

    private TargetSchema readAsReader() throws SQLException {
        try (Connection connection = database.connect(schema, reader, readerPassword)) {
            return SchemaQueries.of(product()).read(connection, schema, QUERY_TIMEOUT_SECONDS);
        }
    }

    /**
     * テストの DB を返す（子のクラスが、種類に固有の確かめで使う）。
     *
     * @return テストの DB
     */
    TargetDbTestDatabase database() {
        return database;
    }

    /**
     * 別に作ったスキーマで、種類に固有のものを作って読む。終わったらスキーマを消す。
     *
     * @param sql スキーマの中にものを作る SQL（{@code %s} をスキーマの名前（引用符つき）に置き換える）
     * @return 読んだ写し
     * @throws SQLException 読み取りの失敗
     */
    TargetSchema readExtraSchema(String... sql) throws SQLException {
        String extra = TargetDbTestDatabase.uniqueName("extra_");
        database.createSchema(extra);
        try {
            String quoted = database.q(extra);
            database.execute(
                    Arrays.stream(sql).map(each -> each.replace("%s", quoted)).toArray(String[]::new));
            return readAsAdmin(extra);
        } finally {
            database.dropSchema(extra);
        }
    }

    TargetSchema readAsAdmin(String target) throws SQLException {
        try (Connection connection = database.admin()) {
            return SchemaQueries.of(product()).read(connection, target, QUERY_TIMEOUT_SECONDS);
        }
    }

    private int customerRows() throws SQLException {
        try (Connection connection = database.admin();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + database.q(schema) + "." + database.q(CUSTOMER))) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 接続を包み、問い合わせの準備（prepareStatement）の回数を数える。 */
    private static Connection counting(Connection target, AtomicInteger count) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        count.incrementAndGet();
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Test
    @DisplayName("tables and views of the configured schema are read with four queries and no other schema leaks in")
    void tablesAndViewsOfTheSchemaOnly() throws SQLException {
        AtomicInteger prepared = new AtomicInteger();
        TargetSchema read;
        try (Connection connection = database.connect(schema, reader, readerPassword)) {
            read = SchemaQueries.of(product()).read(counting(connection, prepared), schema, QUERY_TIMEOUT_SECONDS);
        }

        assertThat(read.databaseProduct()).isEqualTo(product());
        assertThat(read.schemaName()).isEqualTo(schema);
        assertThat(read.tables())
                .extracting(TargetTable::name)
                .containsExactlyInAnyOrder(CUSTOMER, ORDER_ITEMS, SYMBOL_TABLE, CUSTOMER_VIEW)
                .doesNotContain(OTHER_TABLE);
        assertThat(read.table(CUSTOMER_VIEW).orElseThrow().view()).isTrue();
        assertThat(read.table(CUSTOMER_VIEW).orElseThrow().primaryKey()).isEmpty();
        assertThat(read.table(CUSTOMER).orElseThrow().view()).isFalse();
        assertThat(prepared.get()).as("テーブルの数によらず4回").isEqualTo(4);
    }

    @Test
    @DisplayName(
            "columns come in definition order with type, length, precision, scale, nullability, default and comment")
    void columnsInDefinitionOrder() throws SQLException {
        TargetTable customer = readAsReader().table(CUSTOMER).orElseThrow();

        assertThat(customer.comment()).isEqualTo(CUSTOMER_COMMENT);
        assertThat(customer.columns())
                .extracting(TargetColumn::name)
                .containsExactly(CUSTOMER_ID, "Name", "note", "amount", "status");
        TargetColumn id = customer.columns().get(0);
        assertThat(id.dbType().typeName()).isEqualTo(expectedIntegerType());
        assertThat(id.nullable()).isFalse();
        TargetColumn name = customer.columns().get(1);
        assertThat(name.dbType().typeName()).isEqualTo("varchar");
        assertThat(name.dbType().length()).isEqualTo(40L);
        assertThat(name.nullable()).isFalse();
        assertThat(name.comment()).isEqualTo(NAME_COMMENT);
        TargetColumn note = customer.columns().get(2);
        assertThat(note.dbType().typeName()).isEqualTo("text");
        assertThat(note.nullable()).isTrue();
        assertThat(note.comment()).as("空白だけのコメントは無し").isNull();
        TargetColumn amount = customer.columns().get(3);
        assertThat(amount.dbType().typeName()).isEqualTo(expectedDecimalType());
        assertThat(amount.dbType().precision()).isEqualTo(10);
        assertThat(amount.dbType().scale()).isEqualTo(2);
        assertThat(amount.defaultValue()).isEqualTo(expectedAmountDefault());
        TargetColumn status = customer.columns().get(4);
        assertThat(status.defaultValue()).isEqualTo(expectedStatusDefault());
        assertThat(status.nullable()).isTrue();
        assertThat(status.comment()).isNull();
        assertThat(readAsReader().table(ORDER_ITEMS).orElseThrow().comment())
                .as("コメントの無い表")
                .isNull();
    }

    @Test
    @DisplayName("names keep the upper and lower case returned by the database")
    void namesKeepCase() throws SQLException {
        TargetSchema read = readAsReader();

        assertThat(read.schemaName()).startsWith("Sales_");
        assertThat(read.table(CUSTOMER)).isPresent();
        assertThat(read.table(CUSTOMER.toLowerCase())).isEmpty();
        assertThat(read.table(CUSTOMER).orElseThrow().primaryKey()).containsExactly(CUSTOMER_ID);
    }

    @Test
    @DisplayName("the primary key follows its own order and only foreign keys to the same schema are read")
    void keys() throws SQLException {
        for (TargetSchema read : new TargetSchema[] {readAsReader(), readAsAdmin(schema)}) {
            TargetTable items = read.table(ORDER_ITEMS).orElseThrow();

            assertThat(items.columns())
                    .extracting(TargetColumn::name)
                    .containsExactly("line_no", "order_id", "customer_id", "other_ref");
            assertThat(items.primaryKey()).containsExactly("order_id", "line_no");
            assertThat(items.foreignKeys()).singleElement().satisfies(fk -> {
                assertThat(fk.name()).isEqualTo(FK_CUSTOMER);
                assertThat(fk.columns()).containsExactly("customer_id");
                assertThat(fk.referencedTable()).isEqualTo(CUSTOMER);
                assertThat(fk.referencedColumns()).containsExactly(CUSTOMER_ID);
            });
        }
        assertThat(readAsAdmin(schema).tables())
                .flatExtracting(TargetTable::foreignKeys)
                .extracting(TargetForeignKey::referencedTable)
                .as("別のスキーマを見られるアカウントでも、別のスキーマへの外部キーは含めない")
                .doesNotContain(OTHER_TABLE);
    }

    @Test
    @DisplayName("names with quotes, spaces and semicolons are read and nothing in the database changes")
    void symbolNames() throws SQLException {
        TargetSchema before = readAsAdmin(schema);
        int rowsBefore = customerRows();

        TargetTable symbol = readAsReader().table(SYMBOL_TABLE).orElseThrow();

        assertThat(symbol.columns()).extracting(TargetColumn::name).containsExactly(SYMBOL_COLUMN, "id");
        assertThat(symbol.primaryKey()).containsExactly("id");
        assertThat(readAsAdmin(schema)).isEqualTo(before);
        assertThat(customerRows()).isEqualTo(rowsBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("the full column type is read for MySQL and MariaDB and is absent for PostgreSQL")
    void fullColumnType() throws SQLException {
        TargetTable customer = readAsReader().table(CUSTOMER).orElseThrow();
        TargetColumn name = customer.columns().get(1);

        if (product() == DatabaseProduct.POSTGRESQL) {
            assertThat(customer.columns())
                    .extracting(column -> column.dbType().columnType())
                    .as("PostgreSQL は型の全体の表記を持たない")
                    .containsOnlyNulls();
            TargetTable flags = readExtraSchema(
                            "CREATE TABLE %s.flags (id INTEGER PRIMARY KEY, b BOOLEAN, bits BIT(1))")
                    .table("flags")
                    .orElseThrow();
            assertThat(flags.columns().get(1).dbType().typeName()).isEqualTo("bool");
            assertThat(flags.columns().get(1).dbType().columnType()).isNull();
            assertThat(flags.columns().get(2).dbType().columnType()).isNull();
            return;
        }
        assertThat(name.dbType().columnType()).isEqualTo("varchar(40)");
        TargetTable flags = readExtraSchema("CREATE TABLE %s.flags (id INT PRIMARY KEY, flag TINYINT(1) NOT NULL,"
                        + " uflag TINYINT(1) UNSIGNED NULL, small TINYINT NULL, bit1 BIT(1) NULL, bit8 BIT(8) NULL)")
                .table("flags")
                .orElseThrow();
        TargetColumn flag = flags.columns().get(1);
        assertThat(flag.dbType().typeName()).isEqualTo("tinyint");
        assertThat(flag.dbType().precision()).as("精度からは見分けられない").isEqualTo(3);
        assertThat(flag.dbType().columnType()).isEqualTo("tinyint(1)");
        // MySQL 8.4 は表示の幅を tinyint(1)（符号あり）にだけ残し、unsigned の付いたものは幅を落とす。MariaDB は残す。
        assertThat(flags.columns().get(2).dbType().columnType())
                .isEqualTo(product() == DatabaseProduct.MYSQL ? "tinyint unsigned" : "tinyint(1) unsigned");
        TargetColumn small = flags.columns().get(3);
        assertThat(small.dbType().typeName()).isEqualTo("tinyint");
        assertThat(small.dbType().columnType()).isNotNull().doesNotStartWith("tinyint(1)");
        TargetColumn bit1 = flags.columns().get(4);
        assertThat(bit1.dbType().typeName()).isEqualTo("bit");
        assertThat(bit1.dbType().precision()).isEqualTo(1);
        assertThat(bit1.dbType().columnType()).isEqualTo("bit(1)");
        assertThat(flags.columns().get(5).dbType().precision()).isEqualTo(8);
    }

    @Test
    @DisplayName("a schema without tables is read as an empty copy")
    void emptySchema() throws SQLException {
        TargetSchema read = readAsAdmin(emptySchema);

        assertThat(read.schemaName()).isEqualTo(emptySchema);
        assertThat(read.tables()).isEmpty();
    }

    @Test
    @DisplayName("an account with read privileges only reads the same schema as the administrator and cannot write")
    void readOnlyAccount() throws SQLException {
        assertThat(readAsReader()).isEqualTo(readAsAdmin(schema));
        try (Connection connection = database.connect(schema, reader, readerPassword);
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO " + database.q(schema) + "."
                            + database.q(CUSTOMER) + " (" + database.q(CUSTOMER_ID) + ", "
                            + database.q("Name") + ") VALUES (2, 'x')"))
                    .as("読み取りの権限だけのアカウントは書き込めない")
                    .isInstanceOf(SQLException.class);
        }
        assertThat(customerRows()).isEqualTo(1);
    }
}
