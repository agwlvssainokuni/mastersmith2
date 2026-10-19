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
package cherry.mastersmith.businessdata.testsupport;

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;

import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp.Target;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp.Writer;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 認可・本文の上限・漏えい・接続の数・時間切れの結合テストで使う、小さな見本のスキーマ（テーブル {@code item}・{@code other}）。
 * クラスごとに名前の重ならないスキーマと読み取りだけのアカウントを作り、終わったら消す。値はすべて架空。
 */
public final class BusinessDataSampleSchema {

    /** 主に使うテーブル。 */
    public static final String ITEM = "item";

    /** ほかのテーブル（403 を確かめる利用者は、こちらを READ で持つ）。 */
    public static final String OTHER = "other";

    /** {@code item} の見本の名前（漏えいの確かめに使う値）。 */
    public static final String SECRET_NAME = "ペン架空-7731";

    private final TargetDbTestDatabase database;

    private final String schema = TargetDbTestDatabase.uniqueName("biz_");

    private final String reader = TargetDbTestDatabase.uniqueName("reader_");

    private final String readerPassword = TestDatabase.randomSecret();

    private final String writer = TargetDbTestDatabase.uniqueName("writer_");

    private final String writerPassword = TestDatabase.randomSecret();

    /**
     * 作る（まだ起動しない）。
     *
     * @param product DB の種類
     */
    public BusinessDataSampleSchema(DatabaseProduct product) {
        this.database = TargetDbTestDatabase.of(product);
    }

    /** コンテナを起動し、スキーマ・表・行・読み取りだけのアカウントを作る。 */
    public void start() {
        database.start();
        database.createSchema(schema);
        String s = database.q(schema) + ".";
        database.execute(
                "CREATE TABLE " + s + database.q(ITEM)
                        + " (id INTEGER NOT NULL PRIMARY KEY, name VARCHAR(200) NULL, price NUMERIC(10,2) NULL, tag "
                        + (database.product() == DatabaseProduct.POSTGRESQL ? "UUID" : "VARCHAR(36)") + " NULL)",
                "CREATE TABLE " + s + database.q(OTHER) + " (id INTEGER NOT NULL PRIMARY KEY, title VARCHAR(50) NULL)",
                "INSERT INTO " + s + database.q(ITEM) + " (id, name, price) VALUES (1, '" + SECRET_NAME
                        + "', 100.00), (2, 'ノート', 250.50), (3, NULL, NULL)",
                "INSERT INTO " + s + database.q(OTHER) + " (id, title) VALUES (1, '見本')");
        database.createReadOnlyAccount(reader, readerPassword, schema);
        // 書き込みのアカウント（U2）。行の操作だけを持ち、DDL を持たない。読み取りだけのテストでは使わない。
        database.createWriterAccount(writer, writerPassword, schema);
    }

    /**
     * 表・ビューを足す（管理者で流す）。足した後に、読み取りだけのアカウントにも読む権限を与える。
     *
     * @param sql 文（{@code %s} はスキーマで修飾した名前の頭。例 {@code "CREATE VIEW %sv AS ..."}）
     */
    public void addObjects(String... sql) {
        String prefix = database.q(schema) + ".";
        String[] statements = new String[sql.length];
        for (int i = 0; i < sql.length; i++) {
            statements[i] = sql[i].replace("%s", prefix);
        }
        database.execute(statements);
        if (database.product() == DatabaseProduct.POSTGRESQL) {
            database.execute(
                    "GRANT SELECT ON ALL TABLES IN SCHEMA " + database.q(schema) + " TO " + database.q(reader),
                    "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA " + database.q(schema) + " TO "
                            + database.q(writer),
                    "GRANT USAGE ON ALL SEQUENCES IN SCHEMA " + database.q(schema) + " TO " + database.q(writer));
        }
    }

    /** アカウントとスキーマを消し、コンテナを止める。 */
    public void stop() {
        try {
            database.dropAccount(writer);
            database.dropAccount(reader);
            database.dropSchema(schema);
        } finally {
            database.stop();
        }
    }

    /**
     * テストの DB を返す。
     *
     * @return テストの DB
     */
    public TargetDbTestDatabase database() {
        return database;
    }

    /**
     * スキーマの名前を返す。
     *
     * @return スキーマの名前
     */
    public String schema() {
        return schema;
    }

    /**
     * 読み取りだけのアカウントの接続先を返す。
     *
     * @return 接続先
     */
    public Target target() {
        return new Target(
                database.product(),
                database.host(),
                database.port(),
                database.connectDatabase(schema),
                schema,
                reader,
                readerPassword);
    }

    /**
     * 読み取りだけのアカウントのユーザー名を返す。
     *
     * @return ユーザー名
     */
    public String reader() {
        return reader;
    }

    /**
     * 読み取りだけのアカウントのパスワードを返す。
     *
     * @return パスワード
     */
    public String readerPassword() {
        return readerPassword;
    }

    /**
     * 書き込みのアカウントを返す（U2）。
     *
     * @return 書き込みのアカウント
     */
    public Writer writer() {
        return new Writer(writer, writerPassword);
    }

    /**
     * スキーマを READ、{@code item} を FULL にし、補助権限 CREATE・DELETE を与えた設定を返す（U2 の書き込みの利用者）。
     *
     * @return 設定
     */
    public Map<PermissionTarget, PermissionValues> writeSettings() {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(schema), new PermissionValues(MainPermission.READ, false, false));
        settings.put(PermissionTarget.table(schema, ITEM), new PermissionValues(MainPermission.FULL, true, true));
        return settings;
    }

    /**
     * 管理者の接続で、表の行の数を数える（アプリとは別の接続。書いた後の確かめ）。
     *
     * @param table テーブル
     * @param where 条件（無ければ null。値はテストの架空の値だけ）
     * @return 行の数
     */
    public long count(String table, String where) {
        return BusinessDataWriteTables.count(database, schema, table, where);
    }

    /**
     * 見本のスキーマの DSL のモデルを作る。
     *
     * @param extra 足すテーブル・ビューの定義
     * @return モデル
     */
    public DslModel model(DslTable... extra) {
        boolean postgres = database.product() == DatabaseProduct.POSTGRESQL;
        String integer = postgres ? "int4" : "int";
        String decimal = postgres ? "numeric" : "decimal";
        DslTable item = table(
                ITEM,
                false,
                List.of("id"),
                column("id")
                        .dbType(integer)
                        .list(true, 1)
                        .search(SearchOperator.EQUALS)
                        .build(),
                column("name").list(true, 2).search(SearchOperator.CONTAINS).build(),
                column("price")
                        .dbType(decimal)
                        .list(true, 3)
                        .search(SearchOperator.RANGE)
                        .build(),
                column("tag")
                        .dbType(postgres ? "uuid" : "varchar")
                        .list(false, null)
                        .search(SearchOperator.EQUALS)
                        .build());
        DslTable other = table(
                OTHER,
                false,
                List.of("id"),
                column("id").dbType(integer).list(true, 1).build(),
                column("title").list(true, 2).build());
        Map<String, DslTable> tables = new LinkedHashMap<>();
        tables.put(ITEM, item);
        tables.put(OTHER, other);
        for (DslTable table : extra) {
            tables.put(table.name(), table);
        }
        return new DslModel(
                BusinessDataDslFixture.HASH,
                2,
                List.of(new DslSchema(schema, new DisplayName("見本", "Sample"), tables)),
                List.of());
    }

    /**
     * スキーマを READ にした設定を返す（{@code item} だけを {@code itemMain} にする）。
     *
     * @param itemMain {@code item} の主権限
     * @return 設定
     */
    public Map<PermissionTarget, PermissionValues> settings(MainPermission itemMain) {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(schema), new PermissionValues(MainPermission.READ, false, false));
        settings.put(PermissionTarget.table(schema, ITEM), new PermissionValues(itemMain, null, null));
        return settings;
    }

    /**
     * 本文の共通の部分（スキーマ・テーブル）に項目を足した対応表を作る。
     *
     * @param table テーブル
     * @param extra 足す項目（名前, 値, ...）
     * @return 本文
     */
    public Map<String, Object> body(String table, Object... extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", schema);
        body.put("table", table);
        for (int i = 0; i < extra.length; i += 2) {
            body.put((String) extra[i], extra[i + 1]);
        }
        return body;
    }
}
