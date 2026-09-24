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
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;

/** MySQL・MariaDB のテストの DB（スキーマはデータベース。管理者は root）。 */
final class MysqlFamilyTestDatabase extends TargetDbTestDatabase {

    private static final int PORT = 3306;

    private final DatabaseProduct product;

    private final JdbcDatabaseContainer<?> container;

    MysqlFamilyTestDatabase(DatabaseProduct product) {
        this.product = product;
        this.container = product == DatabaseProduct.MYSQL
                ? new MySQLContainer(TargetDbImages.mysql()).withPassword(adminPassword)
                : new MariaDBContainer(TargetDbImages.mariadb()).withPassword(adminPassword);
    }

    @Override
    public DatabaseProduct product() {
        return product;
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
        return schema;
    }

    @Override
    protected String adminUser() {
        return "root";
    }

    @Override
    public Connection connect(String schema, String user, String password) throws SQLException {
        String scheme = product == DatabaseProduct.MYSQL ? "mysql" : "mariadb";
        return DriverManager.getConnection(
                "jdbc:" + scheme + "://" + host() + ":" + port() + "/" + schema, user, password);
    }

    @Override
    public void createSchema(String schema) {
        execute("CREATE DATABASE " + q(schema) + " CHARACTER SET utf8mb4");
    }

    @Override
    public void dropSchema(String schema) {
        execute("DROP DATABASE IF EXISTS " + q(schema));
    }

    @Override
    public void createReadOnlyAccount(String user, String password, String schema) {
        execute(
                "CREATE USER '" + user + "'@'%' IDENTIFIED BY '" + password + "'",
                "GRANT SELECT, SHOW VIEW ON " + q(schema) + ".* TO '" + user + "'@'%'");
    }

    @Override
    public void dropAccount(String user) {
        execute("DROP USER IF EXISTS '" + user + "'@'%'");
    }

    @Override
    public void createStandardFixture(String schema, String otherSchema) {
        String s = q(schema) + ".";
        String o = q(otherSchema) + ".";
        execute(
                "CREATE TABLE " + o + q(OTHER_TABLE) + " (id INT NOT NULL PRIMARY KEY)",
                "CREATE TABLE " + s + q(CUSTOMER) + " ("
                        + q(CUSTOMER_ID) + " INT NOT NULL,"
                        + " Name VARCHAR(40) NOT NULL COMMENT '" + NAME_COMMENT + "',"
                        + " note TEXT NULL COMMENT '   ',"
                        + " amount DECIMAL(10,2) NOT NULL DEFAULT 0,"
                        + " status VARCHAR(10) NULL DEFAULT 'new',"
                        + " PRIMARY KEY (" + q(CUSTOMER_ID) + "))"
                        + " COMMENT '" + CUSTOMER_COMMENT + "'",
                "CREATE TABLE " + s + q(ORDER_ITEMS) + " ("
                        + " line_no INT NOT NULL,"
                        + " order_id INT NOT NULL,"
                        + " customer_id INT NULL,"
                        + " other_ref INT NULL,"
                        + " PRIMARY KEY (order_id, line_no),"
                        + " CONSTRAINT " + q(FK_CUSTOMER) + " FOREIGN KEY (customer_id)"
                        + " REFERENCES " + s + q(CUSTOMER) + " (" + q(CUSTOMER_ID) + "),"
                        + " CONSTRAINT " + q(FK_OTHER) + " FOREIGN KEY (other_ref)"
                        + " REFERENCES " + o + q(OTHER_TABLE) + " (id))",
                "CREATE TABLE " + s + q(SYMBOL_TABLE) + " (" + q(SYMBOL_COLUMN)
                        + " INT NOT NULL, id INT NOT NULL PRIMARY KEY)",
                "CREATE VIEW " + s + q(CUSTOMER_VIEW) + " AS SELECT " + q(CUSTOMER_ID) + ", Name FROM " + s
                        + q(CUSTOMER),
                "INSERT INTO " + s + q(CUSTOMER) + " (" + q(CUSTOMER_ID) + ", Name) VALUES (1, 'テスト')");
    }
}
