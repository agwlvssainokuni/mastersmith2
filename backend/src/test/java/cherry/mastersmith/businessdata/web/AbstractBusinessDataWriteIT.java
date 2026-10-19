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
package cherry.mastersmith.businessdata.web;

import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.CASCADE_CHILD;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.CONFLICT;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.DUP;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.ITEM;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.LINE;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.LOCKED;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.NO_KEY;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.ODD_BRANCH;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.PARENT;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.SERIAL;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.STUCK_VIEW;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables.TRICKY_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * 業務データの作成・更新・削除を、3種類の対象DB で確かめる共通の結合テスト（Intent 261016-business-data-views の U2、計画の Step 18、
 * BR2・BR5〜BR8・BR10、NFR3.1・NFR4.3・NFR4.9、D1〜D3）。子のクラスで DB の種類を決める。
 *
 * <p>アプリは、読み取りだけのアカウントで読み、行の操作だけを持つ書き込みのアカウントで書く。書いた後の値は、アプリとは別の接続（管理者）
 * で読み直して確かめ、アプリのトランザクションの巻き戻しに頼らない。表はテストのクラスごとのスキーマに作り、終わったらスキーマごと消す。
 *
 * <p>競合（D1）は、トリガーで番号を出す形と、管理者の接続で行を排他して書き込みのアカウントの待ちの上限（PostgreSQL は
 * {@code lock_timeout}、MySQL・MariaDB は {@code innodb_lock_wait_timeout}。どちらも 2 秒）を切らせる形で起こす。MariaDB の本物の
 * デッドロックが全体を巻き戻す振る舞いは、分類の部品の単体テストで確かめる（計画の前提）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
@SuppressWarnings("unchecked")
abstract class AbstractBusinessDataWriteIT {

    private final TargetDbTestDatabase database = TargetDbTestDatabase.of(product());

    private final String schema = TargetDbTestDatabase.uniqueName("bizw_");

    private final String reader = TargetDbTestDatabase.uniqueName("reader_");

    private final String readerPassword = TestDatabase.randomSecret();

    private final String writer = TargetDbTestDatabase.uniqueName("writer_");

    private final String writerPassword = TestDatabase.randomSecret();

    private BusinessDataWriteTables tables;

    private BusinessDataTestApp app;

    private Actor member;

    private Map<String, Object> definitionId;

    /**
     * 確かめる DB の種類を返す。
     *
     * @return 種類
     */
    abstract DatabaseProduct product();

    private boolean postgres() {
        return product() == DatabaseProduct.POSTGRESQL;
    }

    @BeforeAll
    void start(@TempDir Path dir) {
        database.start();
        database.createSchema(schema);
        tables = new BusinessDataWriteTables(database, schema);
        tables.create();
        database.createReadOnlyAccount(reader, readerPassword, schema);
        database.createWriterAccount(writer, writerPassword, schema);
        if (postgres()) {
            database.execute("ALTER ROLE " + database.q(writer) + " SET lock_timeout = '2s'");
        } else {
            database.execute("SET GLOBAL innodb_lock_wait_timeout = 2");
        }
        app = BusinessDataTestApp.start(
                dir.resolve("main"),
                new BusinessDataTestApp.Target(
                        product(),
                        database.host(),
                        database.port(),
                        database.connectDatabase(schema),
                        schema,
                        reader,
                        readerPassword),
                new BusinessDataTestApp.Writer(writer, writerPassword),
                new Class<?>[0]);
        app.installDsl(tables.model());
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(schema), new PermissionValues(MainPermission.FULL, true, true));
        member = app.reader(settings);
        definitionId = app.definitionId(member.token(), schema, ITEM);
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
            database.dropAccount(writer);
            database.dropAccount(reader);
            database.dropSchema(schema);
        } finally {
            database.stop();
        }
    }

    // ── 送る道具 ──

    private Map<String, Object> body(String table) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", schema);
        body.put("table", table);
        body.put("definitionId", definitionId);
        return body;
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private HttpResponse<String> create(String table, Map<String, String> values) {
        Map<String, Object> body = body(table);
        body.put("values", values);
        return app.post(BusinessDataPaths.ROWS, member.token(), body);
    }

    private HttpResponse<String> update(String table, Map<String, String> key, Map<String, String> values) {
        Map<String, Object> body = body(table);
        body.put("key", key);
        body.put("values", values);
        return app.patch(BusinessDataPaths.ROWS, member.token(), body);
    }

    private HttpResponse<String> delete(String table, Map<String, String> key) {
        Map<String, Object> body = body(table);
        body.put("key", key);
        return app.post(BusinessDataPaths.DELETE, member.token(), body);
    }

    private static Map<String, Object> ok(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return HttpTestClient.json(response);
    }

    private static Map<String, Object> problem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        Map<String, Object> json = HttpTestClient.json(response);
        assertThat(json).containsEntry("code", code);
        return json;
    }

    private static List<Map<String, Object>> fieldErrors(Map<String, Object> problem) {
        Object errors = problem.get("fieldErrors");
        return errors == null ? List.of() : (List<Map<String, Object>>) errors;
    }

    private static Map<String, Object> fieldError(String field, String reason) {
        Map<String, Object> error = new HashMap<>();
        error.put("field", field);
        error.put("reason", reason);
        return error;
    }

    private String id(Map<String, Object> created) {
        return (String) ((Map<String, Object>) created.get("key")).get("id");
    }

    /** 日時を UTC の文字で読み直す（PostgreSQL は {@code timestamptz} の文字、MySQL・MariaDB は UTC のセッションの文字）。 */
    private String text(String table, String column, String where) {
        String cast = postgres() ? column + "::text" : "CAST(" + column + " AS CHAR)";
        return BusinessDataWriteTables.query(
                        database,
                        "SELECT " + cast + " AS v FROM " + database.q(schema) + "." + database.q(table) + " WHERE "
                                + where)
                .getFirst()
                .get("v");
    }

    // ── 作成 ──

    @Test
    @DisplayName("create writes only the sent columns, applies defaults and returns only the generated primary key")
    void createSentColumnsOnly() {
        Map<String, Object> created = ok(create(ITEM, values("name", "新規A", "code", "C-NEW-A")), 201);

        Map<String, Object> key = (Map<String, Object>) created.get("key");
        assertThat(key).as("生成された鍵は主キーだけ").containsOnlyKeys("id");
        assertThat(created).containsKey("definitionId");
        Map<String, String> row = tables.row(ITEM, "id = " + id(created));
        assertThat(row)
                .containsEntry("name", "新規A")
                .containsEntry("note", "def")
                .containsEntry("code", "C-NEW-A")
                .containsEntry("soft", null)
                .containsEntry("qty", null);
        assertThat(Integer.parseInt(id(created))).as("自動の採番").isGreaterThan(100);
    }

    @Test
    @DisplayName("a PostgreSQL serial key is returned after create")
    void createSerial() {
        assumeTrue(postgres(), "serial は PostgreSQL だけ");

        Map<String, Object> created = ok(create(SERIAL, values("v", "s")), 201);

        assertThat(tables.row(SERIAL, "id = " + id(created))).containsEntry("v", "s");
    }

    @Test
    @DisplayName("generated keys named in mixed case and by a reserved word are returned and the row is written")
    void generatedKeyNames() {
        // コードのレビューの R-02: 送らなかった主キーの名前をドライバーに渡して生成された鍵を読む形が、大文字と小文字の混じる名前と
        // 予約語の名前でも働くこと。
        Map<String, Object> mixed = ok(create(BusinessDataWriteTables.CASE_KEY, values("v", "大小")), 201);
        Map<String, Object> reserved = ok(create(BusinessDataWriteTables.RESERVED_KEY, values("v", "予約")), 201);

        Map<String, Object> mixedKey = (Map<String, Object>) mixed.get("key");
        Map<String, Object> reservedKey = (Map<String, Object>) reserved.get("key");
        assertThat(mixedKey).containsOnlyKeys(BusinessDataWriteTables.CASE_KEY_COLUMN);
        assertThat(reservedKey).containsOnlyKeys(BusinessDataWriteTables.RESERVED_KEY_COLUMN);
        String mixedId = (String) mixedKey.get(BusinessDataWriteTables.CASE_KEY_COLUMN);
        String reservedId = (String) reservedKey.get(BusinessDataWriteTables.RESERVED_KEY_COLUMN);
        assertThat(tables.row(
                        BusinessDataWriteTables.CASE_KEY,
                        database.q(BusinessDataWriteTables.CASE_KEY_COLUMN) + " = " + mixedId))
                .containsEntry("v", "大小");
        assertThat(tables.row(
                        BusinessDataWriteTables.RESERVED_KEY,
                        database.q(BusinessDataWriteTables.RESERVED_KEY_COLUMN) + " = " + reservedId))
                .containsEntry("v", "予約");
        Map<String, Object> detail = ok(
                app.post(
                        BusinessDataPaths.GET,
                        member.token(),
                        Map.of(
                                "schema",
                                schema,
                                "table",
                                BusinessDataWriteTables.RESERVED_KEY,
                                "key",
                                Map.of(BusinessDataWriteTables.RESERVED_KEY_COLUMN, reservedId))),
                200);
        assertThat((Map<String, Object>) detail.get("values")).containsEntry("v", "予約");
    }

    @Test
    @DisplayName("a table without a key can be created into, has no key in the response and stores tricky text as is")
    void createWithoutKey() {
        Map<String, Object> created = ok(create(NO_KEY, values("txt", TRICKY_VALUE)), 201);

        assertThat(created).doesNotContainKey("key");
        assertThat(tables.count(NO_KEY, null)).isEqualTo(1);
        assertThat(tables.rows(NO_KEY, null).getFirst()).containsEntry("txt", TRICKY_VALUE);
    }

    @Test
    @DisplayName("a missing required column from the database is 400 REQUIRED and nothing is written")
    void requiredFromDatabase() {
        long before = tables.count(ITEM, null);

        Map<String, Object> problem = problem(create(ITEM, values("note", "x")), 400, "VALIDATION_FAILED");

        assertThat(fieldErrors(problem)).containsExactly(fieldError("name", "REQUIRED"));
        assertThat(tables.count(ITEM, null)).isEqualTo(before);
    }

    // ── 更新 ──

    @Test
    @DisplayName("update writes only the changed columns, the same value counts as one row, the last write wins")
    void updateChangedColumns() {
        Map<String, String> before = tables.row(ITEM, "id = 1");

        ok(update(ITEM, values("id", "1"), values("note", "更新1")), 200);
        ok(update(ITEM, values("id", "1"), values("note", "更新1")), 200);
        ok(update(ITEM, values("id", "1"), values("note", "更新2")), 200);

        Map<String, String> after = tables.row(ITEM, "id = 1");
        assertThat(after).containsEntry("note", "更新2");
        assertThat(after).containsEntry("name", before.get("name")).containsEntry("code", before.get("code"));
    }

    @Test
    @DisplayName("an empty string and null are stored as an empty string and NULL")
    void emptyAndNull() {
        ok(update(ITEM, values("id", "2"), values("note", "")), 200);
        assertThat(tables.row(ITEM, "id = 2")).containsEntry("note", "");

        ok(update(ITEM, values("id", "2"), values("note", null)), 200);
        assertThat(tables.row(ITEM, "id = 2")).containsEntry("note", null);
    }

    @Test
    @DisplayName(
            "a composite key with slashes, percent signs, question marks, hashes, spaces and quotes points one row")
    void compositeKey() {
        ok(update(LINE, values("order_no", "1", "branch", ODD_BRANCH), values("qty", "9")), 200);
        ok(delete(LINE, values("order_no", "2", "branch", "a")), 200);

        assertThat(tables.rows(LINE, "order_no = 1 AND qty = 9")).hasSize(1);
        assertThat(tables.rows(LINE, "order_no = 1 AND branch = 'a'").getFirst())
                .containsEntry("qty", "1");
        assertThat(tables.count(LINE, "order_no = 2")).isZero();
    }

    // ── 削除 ──

    @Test
    @DisplayName("delete leaves ON DELETE CASCADE to the database and a restricting reference is 409 SAVE_REJECTED")
    void deleteAndReferences() {
        ok(delete(PARENT, values("id", "3")), 200);
        assertThat(tables.count(PARENT, "id = 3")).isZero();
        assertThat(tables.count(CASCADE_CHILD, "parent_id = 3")).as("子は DB が消す").isZero();

        Map<String, Object> problem = problem(delete(PARENT, values("id", "1")), 409, "BUSINESS_SAVE_REJECTED");

        assertThat(fieldErrors(problem)).isEmpty();
        assertThat(tables.count(PARENT, "id = 1")).as("巻き戻した").isEqualTo(1);
    }

    @Test
    @DisplayName("0 rows hit is 404 and 2 rows hit is 409 with the change rolled back")
    void rowCounts() {
        problem(update(ITEM, values("id", "999"), values("note", "x")), 404, "BUSINESS_ROW_NOT_FOUND");
        problem(delete(ITEM, values("id", "999")), 404, "BUSINESS_ROW_NOT_FOUND");

        problem(update(DUP, values("grp", "1"), values("v", "changed")), 409, "BUSINESS_ROW_NOT_UNIQUE");
        problem(delete(DUP, values("grp", "1")), 409, "BUSINESS_ROW_NOT_UNIQUE");

        assertThat(tables.count(DUP, "grp = 1")).as("2行とも残る").isEqualTo(2);
        assertThat(tables.count(DUP, "v = 'changed'")).as("巻き戻した").isZero();
    }

    // ── 一意 ──

    @Test
    @DisplayName("duplicates found by the pre-check and by the constraint give the same 409 code with the column")
    void duplicates() {
        long before = tables.count(ITEM, null);

        Map<String, Object> dslAndDb =
                problem(create(ITEM, values("name", "重複", "code", "C-1")), 409, "BUSINESS_DUPLICATE_VALUE");
        Map<String, Object> dslOnly =
                problem(create(ITEM, values("name", "重複", "soft", "S-1")), 409, "BUSINESS_DUPLICATE_VALUE");
        Map<String, Object> dbOnly =
                problem(create(ITEM, values("name", "重複", "hard", "H-1")), 409, "BUSINESS_DUPLICATE_VALUE");
        Map<String, Object> onUpdate =
                problem(update(ITEM, values("id", "2"), values("code", "C-1")), 409, "BUSINESS_DUPLICATE_VALUE");

        assertThat(fieldErrors(dslAndDb)).containsExactly(fieldError("code", "DUPLICATE"));
        assertThat(fieldErrors(dslOnly)).containsExactly(fieldError("soft", "DUPLICATE"));
        assertThat(fieldErrors(dbOnly)).as("DSL で unique でないカラムは示さない").containsExactly(fieldError("", "DUPLICATE"));
        assertThat(fieldErrors(onUpdate)).containsExactly(fieldError("code", "DUPLICATE"));
        ok(update(ITEM, values("id", "1"), values("code", "C-1")), 200);
        assertThat(tables.count(ITEM, null)).isEqualTo(before);
        assertThat(tables.row(ITEM, "id = 2")).containsEntry("code", "C-2");
    }

    @Test
    @DisplayName(
            "a duplicate committed by another transaction after the pre-check is found by the check after the violation")
    void duplicateAfterPrecheck() throws Exception {
        try (Connection other = database.admin()) {
            other.setAutoCommit(false);
            try (Statement statement = other.createStatement()) {
                statement.executeUpdate("INSERT INTO " + database.q(schema) + "." + database.q(ITEM)
                        + " (id, name, code) VALUES (9001, '割り込み', 'C-RACE')");
            }
            CompletableFuture<HttpResponse<String>> response =
                    CompletableFuture.supplyAsync(() -> create(ITEM, values("name", "後から", "code", "C-RACE")));
            awaitLockWait();
            other.commit();

            Map<String, Object> problem = problem(response.get(30, TimeUnit.SECONDS), 409, "BUSINESS_DUPLICATE_VALUE");

            assertThat(fieldErrors(problem)).containsExactly(fieldError("code", "DUPLICATE"));
        }
        assertThat(tables.count(ITEM, "code = 'C-RACE'")).isEqualTo(1);
    }

    /** 書き込みのアカウントの接続が、ほかのトランザクションの排他を待ち始めるまで待つ。 */
    private void awaitLockWait() throws InterruptedException {
        String sql = postgres()
                ? "SELECT COUNT(*) FROM pg_stat_activity WHERE usename = '" + writer + "' AND wait_event_type = 'Lock'"
                // MySQL・MariaDB の INNODB_TRX は待ちを示さなかったため、書き込みのアカウントの INSERT が実行中のままかで見る（相手の行が
                // 確定するまで終わらない。MariaDB は文の上限を SET STATEMENT … FOR で頭に付ける）。
                : "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER = '" + writer
                        + "' AND COMMAND = 'Query' AND INFO LIKE '%INSERT INTO%'";
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection connection = database.admin();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(sql)) {
                resultSet.next();
                if (resultSet.getLong(1) > 0) {
                    return;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("待ちの確かめに失敗しました", e);
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("書き込みのアカウントが待ちに入りませんでした");
    }

    // ── 外部キー・NOT NULL・DB の拒否 ──

    @Test
    @DisplayName("a missing reference on create and update is 409 REFERENCE_NOT_FOUND with the column")
    void missingReference() {
        Map<String, Object> onCreate =
                problem(create(ITEM, values("name", "参照", "parent_id", "999")), 409, "BUSINESS_REFERENCE_NOT_FOUND");
        Map<String, Object> onUpdate = problem(
                update(ITEM, values("id", "2"), values("parent_id", "999")), 409, "BUSINESS_REFERENCE_NOT_FOUND");

        assertThat(fieldErrors(onCreate)).containsExactly(fieldError("parent_id", "REFERENCE_NOT_FOUND"));
        assertThat(fieldErrors(onUpdate)).containsExactly(fieldError("parent_id", "REFERENCE_NOT_FOUND"));
        assertThat(tables.count(ITEM, "name = '参照'")).isZero();
        assertThat(tables.row(ITEM, "id = 2")).containsEntry("parent_id", null);
    }

    @Test
    @DisplayName("NOT NULL of a column the user cannot enter and of an entered column give different reasons")
    void notNull() {
        Map<String, Object> notEditable =
                problem(create(LOCKED, values("id", "5", "v", "a")), 409, "BUSINESS_SAVE_REJECTED");
        Map<String, Object> entered =
                problem(update(LOCKED, values("id", "1"), values("v", null)), 409, "BUSINESS_SAVE_REJECTED");
        Map<String, Object> two =
                problem(update(LOCKED, values("id", "1"), values("v", null, "w", null)), 409, "BUSINESS_SAVE_REJECTED");

        assertThat(fieldErrors(notEditable)).containsExactly(fieldError("", "NOT_EDITABLE_NOT_NULL"));
        assertThat(fieldErrors(entered)).containsExactly(fieldError("v", "NOT_NULL"));
        assertThat(fieldErrors(two)).containsExactly(fieldError("", "NOT_NULL"));
        assertThat(tables.count(LOCKED, "id = 5")).isZero();
        assertThat(tables.row(LOCKED, "id = 1")).containsEntry("v", "a").containsEntry("w", "b");
    }

    @Test
    @DisplayName("CHECK, a view that cannot be updated and a format error are 409 SAVE_REJECTED without a column")
    void databaseRejections() {
        Map<String, Object> check =
                problem(create(ITEM, values("name", "負", "qty", "-1")), 409, "BUSINESS_SAVE_REJECTED");
        Map<String, Object> view =
                problem(create(STUCK_VIEW, values("id", "7", "n", "1")), 409, "BUSINESS_SAVE_REJECTED");
        Map<String, Object> format =
                problem(update(CONFLICT, values("id", "1"), values("v", "format")), 409, "BUSINESS_SAVE_REJECTED");

        assertThat(fieldErrors(check)).isEmpty();
        assertThat(fieldErrors(view)).isEmpty();
        assertThat(fieldErrors(format)).as("D2").isEmpty();
        assertThat(tables.count(ITEM, "name = '負'")).isZero();
        assertThat(tables.row(CONFLICT, "id = 1")).containsEntry("v", "a");
    }

    @Test
    @DisplayName("PostgreSQL NUL characters are 409 SAVE_REJECTED")
    void postgresNul() {
        assumeTrue(postgres(), "NUL を拒むのは PostgreSQL");

        problem(create(ITEM, values("name", "a\u0000b")), 409, "BUSINESS_SAVE_REJECTED");
    }

    @Test
    @DisplayName("MySQL and MariaDB TIMESTAMP after its range and characters outside latin1 are 409 SAVE_REJECTED")
    void mysqlRanges() {
        assumeTrue(!postgres(), "TIMESTAMP の範囲と文字集合は MySQL・MariaDB");

        // MariaDB 11.5 からの TIMESTAMP は 2106 年まで入るため、両方の範囲の外の 2107 年にする。
        problem(create(ITEM, values("name", "範囲", "at_tz", "2107-01-01T00:00:00Z")), 409, "BUSINESS_SAVE_REJECTED");
        problem(create(ITEM, values("name", "文字", "latin", "日本語")), 409, "BUSINESS_SAVE_REJECTED");

        assertThat(tables.count(ITEM, "name IN ('範囲', '文字')")).isZero();
    }

    // ── 競合（D1） ──

    @Test
    @DisplayName("deadlocks, serialization failures and lock waits are 503 UNAVAILABLE and nothing is written")
    void conflicts() throws Exception {
        problem(
                update(CONFLICT, values("id", "1"), values("v", BusinessDataWriteTables.DEADLOCK)),
                503,
                "BUSINESS_TARGET_DB_UNAVAILABLE");
        problem(
                update(CONFLICT, values("id", "1"), values("v", BusinessDataWriteTables.SERIALIZE)),
                503,
                "BUSINESS_TARGET_DB_UNAVAILABLE");
        try (Connection other = database.admin()) {
            other.setAutoCommit(false);
            try (Statement statement = other.createStatement()) {
                statement
                        .executeQuery("SELECT v FROM " + database.q(schema) + "." + database.q(CONFLICT)
                                + " WHERE id = 2 FOR UPDATE")
                        .close();
            }
            problem(update(CONFLICT, values("id", "2"), values("v", "待ち")), 503, "BUSINESS_TARGET_DB_UNAVAILABLE");
            other.rollback();
        }

        assertThat(tables.row(CONFLICT, "id = 1")).containsEntry("v", "a");
        assertThat(tables.row(CONFLICT, "id = 2")).containsEntry("v", "b");
    }

    // ── 境界の値と時差 ──

    @Test
    @DisplayName("BIGINT limits, DECIMAL(38,10) and NULL are written without rounding and read back digit by digit")
    void boundaryValues() {
        String high = id(ok(
                create(
                        ITEM,
                        values(
                                "name", "上限",
                                "big", "9223372036854775807",
                                "amount", "1234567890123456789012345678.0123456789")),
                201));
        String low = id(ok(
                create(
                        ITEM,
                        values("name", "下限", "big", "-9223372036854775808", "amount", "-0.0000000001", "day", null)),
                201));

        assertThat(tables.row(ITEM, "id = " + high)).containsEntry("big", "9223372036854775807");
        assertThat(text(ITEM, "amount", "id = " + high)).isEqualTo("1234567890123456789012345678.0123456789");
        assertThat(tables.row(ITEM, "id = " + low))
                .containsEntry("big", "-9223372036854775808")
                .containsEntry("day", null);
        assertThat(text(ITEM, "amount", "id = " + low)).isEqualTo("-0.0000000001");
        HttpResponse<String> overResponse = create(ITEM, values("name", "超え", "big", "9223372036854775808"));
        if (postgres()) {
            Map<String, Object> over = problem(overResponse, 400, "VALIDATION_FAILED");
            assertThat(fieldErrors(over)).containsExactly(fieldError("big", "OUT_OF_RANGE"));
        } else {
            // MySQL・MariaDB の bigint は DSL の型の名前から符号なしかが分からず、サーバーの検証は符号なしの上限まで通す。符号つきの
            // カラムでは DB が拒む（generation-notes.md の G13）。
            problem(overResponse, 409, "BUSINESS_SAVE_REJECTED");
        }
        assertThat(tables.count(ITEM, "name = '超え'")).isZero();
    }

    @Test
    @DisplayName("zoned values without an offset, with Z and with +09:00 are the same instant whatever the JVM zone")
    void zonedValues() {
        Set<String> stored = new HashSet<>();
        for (String value : List.of("2026-03-08T01:30:00", "2026-03-08T01:30:00Z", "2026-03-08T10:30:00+09:00")) {
            String id = id(ok(create(ITEM, values("name", "時差", "at_tz", value)), 201));
            stored.add(text(ITEM, "at_tz", "id = " + id));
        }
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            String id = id(ok(create(ITEM, values("name", "時差", "at_tz", "2026-03-08T01:30:00Z")), 201));
            stored.add(text(ITEM, "at_tz", "id = " + id));
        } finally {
            TimeZone.setDefault(original);
        }

        assertThat(stored).hasSize(1);
        assertThat(stored.iterator().next()).startsWith("2026-03-08 01:30:00");
    }

    @Test
    @DisplayName("fractions of a second up to the column precision are kept and one more digit is 400")
    void fractionBoundary() {
        String id = id(ok(create(ITEM, values("name", "端数", "at", "2026-03-08T02:30:00.123456")), 201));

        assertThat(text(ITEM, "at", "id = " + id)).isEqualTo("2026-03-08 02:30:00.123456");
        Map<String, Object> over = problem(
                create(ITEM, values("name", "端数", "at", "2026-03-08T02:30:00.1234567")), 400, "VALIDATION_FAILED");
        assertThat(fieldErrors(over)).containsExactly(fieldError("at", "TOO_MANY_DIGITS"));
    }

    // ── 識別子と値 ──

    @Test
    @DisplayName(
            "quotes, semicolons, dashes, backquotes and newlines in values are stored as text and in names are refused")
    void injection() {
        String id = id(ok(create(ITEM, values("name", TRICKY_VALUE, "note", "x' OR '1'='1")), 201));

        assertThat(tables.row(ITEM, "id = " + id))
                .containsEntry("name", TRICKY_VALUE)
                .containsEntry("note", "x' OR '1'='1");
        Map<String, Object> badTable = body(ITEM + "; DROP TABLE " + PARENT + " --");
        badTable.put("values", values("name", "x"));
        problem(app.post(BusinessDataPaths.ROWS, member.token(), badTable), 404, "BUSINESS_TABLE_NOT_FOUND");
        Map<String, Object> badColumn =
                problem(create(ITEM, values("name", "x", "note`; DROP TABLE x; --\n", "y")), 400, "VALIDATION_FAILED");
        assertThat(fieldErrors(badColumn)).containsExactly(fieldError("note`; DROP TABLE x; --\n", "NOT_ALLOWED"));
        assertThat(tables.count(PARENT, null)).as("表は消えていない").isPositive();
    }

    @Test
    @DisplayName("a structurally forbidden update of a table without a key is 409 before touching the database")
    void structural() {
        problem(update(NO_KEY, values("txt", "x"), values("txt", "y")), 409, "BUSINESS_ROW_OPERATION_NOT_ALLOWED");
        problem(delete(NO_KEY, values("txt", "x")), 409, "BUSINESS_ROW_OPERATION_NOT_ALLOWED");
    }

    // ── 定義と接続のアカウント ──

    @Test
    @DisplayName("the definition says writes are configured and carries the validation items for every column")
    void definitionForWrites() {
        Map<String, Object> definition = HttpTestClient.json(
                app.post(BusinessDataPaths.DEFINITION, member.token(), Map.of("schema", schema, "table", ITEM)));

        assertThat(definition).containsEntry("writeConfigured", true);
        assertThat((List<Object>) definition.get("operations")).contains("CREATE", "UPDATE", "DELETE");
        Map<String, Object> name = ((List<Map<String, Object>>) definition.get("detailColumns"))
                .stream()
                        .filter(c -> "name".equals(c.get("column")))
                        .findFirst()
                        .orElseThrow();
        assertThat(name)
                .containsEntry("required", true)
                .containsEntry("nullable", false)
                .containsEntry("maxLength", 100)
                .containsEntry("editableOnCreate", true);
    }

    @Test
    @DisplayName("writes use the writer account and reads use the read-only account")
    void accounts() {
        ok(create(ITEM, values("name", "アカウント")), 201);
        ok(
                app.post(
                        BusinessDataPaths.SEARCH,
                        member.token(),
                        Map.of("schema", schema, "table", ITEM, "page", 1, "size", 20)),
                200);

        String sql = postgres()
                ? "SELECT DISTINCT usename AS u FROM pg_stat_activity WHERE usename IN ('" + reader + "', '" + writer
                        + "')"
                : "SELECT DISTINCT user AS u FROM information_schema.PROCESSLIST WHERE user IN ('" + reader + "', '"
                        + writer + "')";
        Set<String> users = new HashSet<>();
        BusinessDataWriteTables.query(database, sql).forEach(row -> users.add(row.get("u")));

        assertThat(users).containsExactlyInAnyOrder(reader, writer);
    }
}
