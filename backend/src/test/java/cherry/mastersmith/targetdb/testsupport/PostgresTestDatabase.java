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

import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER_COMMENT;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER_ID;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.CUSTOMER_VIEW;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.FK_CUSTOMER;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.FK_OTHER;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.NAME_COMMENT;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.ORDER_ITEMS;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.OTHER_TABLE;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.SYMBOL_COLUMN;
import static cherry.mastersmith.targetdb.testsupport.TargetDbFixture.SYMBOL_TABLE;

import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL のテストの DB（スキーマは1つの DB の中に作る。管理者はコンテナの作る利用者）。 */
final class PostgresTestDatabase extends TargetDbTestDatabase {

    private static final int PORT = 5432;

    private final PostgreSQLContainer container =
            new PostgreSQLContainer(TargetDbImages.postgres()).withPassword(adminPassword);

    @Override
    public DatabaseProduct product() {
        return DatabaseProduct.POSTGRESQL;
    }

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return container;
    }

    @Override
    public int port() {
        return container.getMappedPort(PORT);
    }

    @Override
    public String connectDatabase(String schema) {
        return container.getDatabaseName();
    }

    @Override
    protected String adminUser() {
        return container.getUsername();
    }

    @Override
    public Connection connect(String schema, String user, String password) throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), user, password);
    }

    @Override
    public void createSchema(String schema) {
        execute("CREATE SCHEMA " + q(schema));
    }

    @Override
    public void dropSchema(String schema) {
        execute("DROP SCHEMA IF EXISTS " + q(schema) + " CASCADE");
    }

    @Override
    public void createReadOnlyAccount(String user, String password, String schema) {
        execute(
                "CREATE ROLE " + q(user) + " LOGIN PASSWORD '" + password + "'",
                "GRANT USAGE ON SCHEMA " + q(schema) + " TO " + q(user),
                "GRANT SELECT ON ALL TABLES IN SCHEMA " + q(schema) + " TO " + q(user));
    }

    @Override
    public void dropAccount(String user) {
        execute("DROP OWNED BY " + q(user), "DROP ROLE IF EXISTS " + q(user));
    }

    @Override
    public void createStandardFixture(String schema, String otherSchema) {
        String s = q(schema) + ".";
        String o = q(otherSchema) + ".";
        execute(
                "CREATE TABLE " + o + q(OTHER_TABLE) + " (id INTEGER NOT NULL PRIMARY KEY)",
                "CREATE TABLE " + s + q(CUSTOMER) + " ("
                        + q(CUSTOMER_ID) + " INTEGER NOT NULL PRIMARY KEY,"
                        + " \"Name\" VARCHAR(40) NOT NULL,"
                        + " note TEXT NULL,"
                        + " amount NUMERIC(10,2) NOT NULL DEFAULT 0,"
                        + " status VARCHAR(10) NULL DEFAULT 'new')",
                "COMMENT ON TABLE " + s + q(CUSTOMER) + " IS '" + CUSTOMER_COMMENT + "'",
                "COMMENT ON COLUMN " + s + q(CUSTOMER) + ".\"Name\" IS '" + NAME_COMMENT + "'",
                "COMMENT ON COLUMN " + s + q(CUSTOMER) + ".note IS '   '",
                "CREATE TABLE " + s + q(ORDER_ITEMS) + " ("
                        + " line_no INTEGER NOT NULL,"
                        + " order_id INTEGER NOT NULL,"
                        + " customer_id INTEGER NULL,"
                        + " other_ref INTEGER NULL,"
                        + " PRIMARY KEY (order_id, line_no),"
                        + " CONSTRAINT " + q(FK_CUSTOMER) + " FOREIGN KEY (customer_id)"
                        + " REFERENCES " + s + q(CUSTOMER) + " (" + q(CUSTOMER_ID) + "),"
                        + " CONSTRAINT " + q(FK_OTHER) + " FOREIGN KEY (other_ref)"
                        + " REFERENCES " + o + q(OTHER_TABLE) + " (id))",
                "CREATE TABLE " + s + q(SYMBOL_TABLE) + " (" + q(SYMBOL_COLUMN)
                        + " INTEGER NOT NULL, id INTEGER NOT NULL PRIMARY KEY)",
                "CREATE VIEW " + s + q(CUSTOMER_VIEW) + " AS SELECT " + q(CUSTOMER_ID) + ", \"Name\" FROM " + s
                        + q(CUSTOMER),
                "INSERT INTO " + s + q(CUSTOMER) + " (" + q(CUSTOMER_ID) + ", \"Name\") VALUES (1, 'テスト')");
    }
}
