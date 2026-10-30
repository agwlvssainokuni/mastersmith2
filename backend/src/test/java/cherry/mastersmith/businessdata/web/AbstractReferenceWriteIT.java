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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.FK_COLUMN;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_MAIN;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_HID;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_INT;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_MANY;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_OK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_PICK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_STR;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_TGT;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_TRICKY;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.businessdata.testsupport.PausingTargetWriteAccess;
import cherry.mastersmith.businessdata.testsupport.PausingWriteAccessConfig;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.businessdata.testsupport.StatementLog;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 作成・更新の保存前の参照の確かめを、3種類の対象DB で確かめる共通の結合テスト（Intent 261029-reference-lookup-cleanup の U2、計画の
 * Step 9、(g)(h)(i)、NFR1.21〜NFR1.24・NFR1.26〜NFR1.29・NFR2.26・NFR2.27・NFR3.27、AC3.1.1〜AC3.1.6・AC3.2.1〜AC3.2.6・
 * AC1.5.2〜AC1.5.4）。子のクラスで DB の種類を決める。
 *
 * <p>表はクラスごとに一意の名前のスキーマ（MySQL・MariaDB はデータベース）に作り、終わったら消す。アプリは読み取りだけのアカウントで
 * 読み、書き込みのアカウントで書く。書いた後の値はアプリとは別の接続（管理者）で読み直す。値はすべて架空。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
@SuppressWarnings("unchecked")
abstract class AbstractReferenceWriteIT {

    private final AtomicInteger ids = new AtomicInteger(1_000);

    private BusinessDataSampleSchema sample;

    private BusinessDataTestApp app;

    private Actor member;

    private Map<String, Object> definitionId;

    private PausingTargetWriteAccess pausing;

    /**
     * 確かめる DB の種類を返す。
     *
     * @return 種類
     */
    abstract DatabaseProduct product();

    private boolean mysqlFamily() {
        return product() != DatabaseProduct.POSTGRESQL;
    }

    @BeforeAll
    void start(@TempDir Path dir) {
        sample = new BusinessDataSampleSchema(product());
        sample.start();
        ReferenceTables tables = new ReferenceTables(sample);
        tables.createWrite();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[] {
            PausingWriteAccessConfig.class, CountingDataSourceConfig.class
        });
        app.installDsl(sample.model(tables.writeTables()));
        member = app.reader(tables.writeSettings(Map.of()));
        definitionId = app.definitionId(member.token(), sample.schema(), WS_MAIN);
        pausing = app.bean(PausingWriteAccessConfig.BEAN, PausingTargetWriteAccess.class);
    }

    @AfterEach
    void resetGate() {
        pausing.reset();
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
        } finally {
            if (sample != null) {
                sample.stop();
            }
        }
    }

    // ── 送る道具 ──

    private int nextId() {
        return ids.incrementAndGet();
    }

    private Map<String, Object> body(String table) {
        Map<String, Object> body = sample.body(table);
        body.put("definitionId", definitionId);
        return body;
    }

    private static Map<String, String> values(Object... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put((String) pairs[i], pairs[i + 1] == null ? null : String.valueOf(pairs[i + 1]));
        }
        return values;
    }

    private HttpResponse<String> create(String table, Map<String, String> values) {
        Map<String, Object> body = body(table);
        body.put("values", values);
        return app.post(BusinessDataPaths.ROWS, member.token(), body);
    }

    private HttpResponse<String> update(String table, int id, Map<String, String> values) {
        Map<String, Object> body = body(table);
        body.put("key", Map.of("id", Integer.toString(id)));
        body.put("values", values);
        return app.patch(BusinessDataPaths.ROWS, member.token(), body);
    }

    private static void created(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
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
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("field", field);
        error.put("reason", reason);
        return error;
    }

    private static void rejected(HttpResponse<String> response, String field) {
        assertThat(fieldErrors(problem(response, 409, "BUSINESS_REFERENCE_NOT_FOUND")))
                .containsExactly(fieldError(field, "REFERENCE_NOT_FOUND"));
    }

    private long count(String table, String where) {
        return sample.count(table, where);
    }

    private Map<String, String> row(String table, int id) {
        List<Map<String, String>> rows = BusinessDataWriteTables.query(
                sample.database(),
                "SELECT * FROM " + sample.database().q(sample.schema()) + "."
                        + sample.database().q(table) + " WHERE id = " + id);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private void admin(String... sql) {
        sample.addObjects(sql);
    }

    private CountingDataSource counting(String name) {
        return (CountingDataSource) app.bean(name, DataSource.class);
    }

    private static String repeat(String text, int count) {
        return text.repeat(count);
    }

    // ── 作成 ──

    @Test
    @DisplayName("a create with existing reference values is saved and read back by another connection (AC3.1.1)")
    void createWithExistingValues() {
        int id = nextId();

        created(create(WS_MAIN, values("id", id, FK_COLUMN, "V1", "ref", "V2", "pick_ref", "V3")));

        assertThat(row(WS_MAIN, id))
                .containsEntry(FK_COLUMN, "V1")
                .containsEntry("ref", "V2")
                .containsEntry("pick_ref", "V3");
    }

    @Test
    @DisplayName("a create with a value missing from a target without a foreign key is 409 and adds no row (AC3.1.2)")
    void createWithMissingValue() {
        int id = nextId();

        rejected(create(WS_MAIN, values("id", id, "ref", "V9")), "ref");

        assertThat(count(WS_MAIN, "id = " + id)).isZero();
    }

    @Test
    @DisplayName("an unreadable value and a missing value get the same answer, and an IDOR value is rejected (AC3.1.3)")
    void unreadableIsLikeMissing() {
        Map<String, Object> unreadable =
                problem(create(WS_MAIN, values("id", nextId(), "hid_ref", "H1")), 409, "BUSINESS_REFERENCE_NOT_FOUND");
        Map<String, Object> missing =
                problem(create(WS_MAIN, values("id", nextId(), "hid_ref", "H9")), 409, "BUSINESS_REFERENCE_NOT_FOUND");
        Map<String, Object> idor =
                problem(create(WS_MAIN, values("id", nextId(), "ref", "M00001")), 409, "BUSINESS_REFERENCE_NOT_FOUND");

        for (String key : List.of("status", "code", "title", "detail", "fieldErrors")) {
            assertThat(unreadable.get(key)).as(key).isEqualTo(missing.get(key));
        }
        assertThat(fieldErrors(idor)).containsExactly(fieldError("ref", "REFERENCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("an extra column is 400 NOT_ALLOWED and every rejected reference is listed in the DSL order")
    void notAllowedAndAllRejections() {
        HttpResponse<String> extra = create(WS_MAIN, values("id", nextId(), "target_table", W_TGT));
        assertThat(fieldErrors(problem(extra, 400, "VALIDATION_FAILED")))
                .extracting(e -> e.get("reason"))
                .containsExactly("NOT_ALLOWED");

        int id = nextId();
        Map<String, Object> both = problem(
                create(WS_MAIN, values("id", id, "ref", "V9", "num_ref", "999", "dec_ref", "1")),
                409,
                "BUSINESS_REFERENCE_NOT_FOUND");
        assertThat(fieldErrors(both))
                .containsExactly(
                        fieldError("ref", "REFERENCE_NOT_FOUND"), fieldError("num_ref", "REFERENCE_NOT_FOUND"));
        assertThat(count(WS_MAIN, "id = " + id)).isZero();
    }

    @Test
    @DisplayName("a NULL in a nullable reference is saved without any check (AC3.1.5)")
    void nullIsNotChecked() {
        int id = nextId();

        created(create(WS_MAIN, values("id", id, "ref", null, "hid_ref", null)));

        assertThat(row(WS_MAIN, id)).containsEntry("ref", null);
    }

    static Stream<Arguments> requiredGrid() {
        String nothing = "<absent>";
        return Stream.of(
                Arguments.of(WS_REQ_OK, "V1", 201, null, null),
                Arguments.of(WS_REQ_OK, "V9", 409, "BUSINESS_REFERENCE_NOT_FOUND", "REFERENCE_NOT_FOUND"),
                Arguments.of(WS_REQ_OK, "", 400, "VALIDATION_FAILED", "REQUIRED"),
                Arguments.of(WS_REQ_OK, null, 400, "VALIDATION_FAILED", "REQUIRED"),
                Arguments.of(WS_REQ_OK, nothing, 400, "VALIDATION_FAILED", "REQUIRED"),
                Arguments.of(WS_REQ_MANY, "M00001", 409, "BUSINESS_REFERENCE_NOT_FOUND", "REFERENCE_NOT_FOUND"),
                Arguments.of(WS_REQ_MANY, "", 409, "BUSINESS_REFERENCE_NOT_FOUND", "NOT_EDITABLE_NOT_NULL"),
                Arguments.of(WS_REQ_MANY, null, 409, "BUSINESS_REFERENCE_NOT_FOUND", "NOT_EDITABLE_NOT_NULL"),
                Arguments.of(WS_REQ_MANY, nothing, 409, "BUSINESS_REFERENCE_NOT_FOUND", "NOT_EDITABLE_NOT_NULL"),
                Arguments.of(WS_REQ_HID, "H1", 409, "BUSINESS_REFERENCE_NOT_FOUND", "REFERENCE_NOT_FOUND"),
                Arguments.of(WS_REQ_HID, "", 409, "BUSINESS_REFERENCE_NOT_FOUND", "NOT_EDITABLE_NOT_NULL"),
                Arguments.of(WS_REQ_HID, null, 409, "BUSINESS_REFERENCE_NOT_FOUND", "NOT_EDITABLE_NOT_NULL"),
                Arguments.of(WS_REQ_HID, nothing, 409, "BUSINESS_REFERENCE_NOT_FOUND", "NOT_EDITABLE_NOT_NULL"),
                Arguments.of(WS_REQ_STR, "", 409, "BUSINESS_REFERENCE_NOT_FOUND", "REFERENCE_NOT_FOUND"),
                Arguments.of(WS_REQ_STR, null, 400, "VALIDATION_FAILED", "NOT_NULL"),
                Arguments.of(WS_REQ_INT, "", 400, "VALIDATION_FAILED", "INVALID_VALUE"),
                Arguments.of(WS_REQ_INT, "7", 201, null, null),
                Arguments.of(WS_REQ_PICK, "V1", 201, null, null),
                Arguments.of(WS_REQ_PICK, nothing, 400, "VALIDATION_FAILED", "REQUIRED"));
    }

    @ParameterizedTest(name = "{0} with [{1}] -> {2} {4}")
    @MethodSource("requiredGrid")
    @DisplayName("functional-spec 4.3: every cell of the required reference grid (AC1.5.2, AC1.5.3)")
    void requiredGrid(String table, String value, int status, String code, String reason) {
        int id = nextId();
        Map<String, String> body = values("id", id, "memo", "m");
        if (!"<absent>".equals(value)) {
            body.put("req", value);
        }

        HttpResponse<String> response = create(table, body);

        if (status == 201) {
            created(response);
            assertThat(count(table, "id = " + id)).isEqualTo(1);
            return;
        }
        assertThat(fieldErrors(problem(response, status, code))).containsExactly(fieldError("req", reason));
        assertThat(count(table, "id = " + id)).isZero();
    }

    @Test
    @DisplayName("a required string reference without REQUIRED that is absent falls to the database NOT NULL as before")
    void absentWithoutRequiredUsesTheDatabase() {
        int id = nextId();

        HttpResponse<String> response = create(WS_REQ_STR, values("id", id, "memo", "m"));

        assertThat(fieldErrors(problem(response, 409, "BUSINESS_SAVE_REJECTED")))
                .containsExactly(fieldError("", "NOT_EDITABLE_NOT_NULL"));
        assertThat(count(WS_REQ_STR, "id = " + id)).isZero();
    }

    // ── 更新 ──

    @Test
    @DisplayName("an unchanged reference is saved even if unreadable or dangling, a changed one is checked (AC3.2.1-3)")
    void updateChecksOnlyChanged() {
        int id = nextId();
        admin("INSERT INTO %s" + WS_MAIN + " (id, name, ref, hid_ref) VALUES (" + id + ", '前', 'GONE', 'H1')");

        HttpResponse<String> unchanged = update(WS_MAIN, id, values("name", "後", "ref", "GONE", "hid_ref", "H1"));
        assertThat(unchanged.statusCode()).as(unchanged.body()).isEqualTo(200);
        assertThat(row(WS_MAIN, id)).containsEntry("name", "後");

        rejected(update(WS_MAIN, id, values("name", "再", "ref", "V9")), "ref");
        rejected(update(WS_MAIN, id, values("hid_ref", "H9")), "hid_ref");
        assertThat(row(WS_MAIN, id)).containsEntry("name", "後").containsEntry("ref", "GONE");

        HttpResponse<String> changed = update(WS_MAIN, id, values("ref", "V2"));
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
        assertThat(row(WS_MAIN, id)).containsEntry("ref", "V2");
    }

    @Test
    @DisplayName("a missing row is 404 before any reference rejection (AC3.2.6)")
    void missingRowFirst() {
        problem(update(WS_MAIN, 99_999, values("ref", "V9")), 404, "BUSINESS_ROW_NOT_FOUND");
    }

    @Test
    @DisplayName("an update of the same value written in another form is not checked (AC3.2.4)")
    void sameValueOtherForm() {
        int id = nextId();
        admin("INSERT INTO %s" + WS_MAIN + " (id, int_ref, dec_ref) VALUES (" + id + ", 5, 2.5)");

        HttpResponse<String> response = update(WS_MAIN, id, values("int_ref", "005", "dec_ref", "2.50", "name", "x"));

        assertThat(response.statusCode()).as("参照先に無い 5・2.5 でも同じ値なので確かめない").isEqualTo(200);
    }

    // ── 比べ方と型（NFR1.28） ──

    @Test
    @DisplayName("NFR1.28: types, number forms, case, trailing spaces and boundary values answer the same on every DB")
    void comparisonAndTypes() {
        created(create(WS_MAIN, values("id", nextId(), "int_ref", "7")));
        created(create(WS_MAIN, values("id", nextId(), "int_ref", "007")));
        created(create(WS_MAIN, values("id", nextId(), "ref", "007")));
        rejected(create(WS_MAIN, values("id", nextId(), "ref", "7")), "ref");
        rejected(create(WS_MAIN, values("id", nextId(), "ref", "k1")), "ref");
        rejected(create(WS_MAIN, values("id", nextId(), "ref", "K1 ")), "ref");
        created(create(WS_MAIN, values("id", nextId(), "ref", "K1")));
        created(create(WS_MAIN, values("id", nextId(), "dec_ref", "1.0")));
        created(create(WS_MAIN, values("id", nextId(), "dec_ref", ReferenceTables.BIG_DECIMAL)));
        created(create(WS_MAIN, values("id", nextId(), "num_ref", ReferenceTables.BIG_MAX)));
        rejected(create(WS_MAIN, values("id", nextId(), "ref", "")), "ref");
    }

    @Test
    @DisplayName("(h): composite keys, no key, views, non unique values and self references never give 500")
    void targetShapes() {
        created(create(WS_MAIN, values("id", nextId(), "comp_ref", "B1")));
        created(create(WS_MAIN, values("id", nextId(), "nokey_ref", "N1")));
        created(create(WS_MAIN, values("id", nextId(), "view_ref", "V1")));
        created(create(WS_MAIN, values("id", nextId(), "dup_ref", "K")));
        int self = nextId();
        rejected(create(WS_MAIN, values("id", self, "self_ref", self)), "self_ref");
        int parent = nextId();
        created(create(WS_MAIN, values("id", parent, "name", "親")));
        created(create(WS_MAIN, values("id", nextId(), "self_ref", parent)));
    }

    @Test
    @DisplayName("NFR1.24: quotes, semicolons, comments, backticks and new lines are never read as SQL")
    void valuesAreNotSql() {
        int id = nextId();
        created(create(WS_MAIN, values("id", id, "ref", W_TRICKY)));
        assertThat(row(WS_MAIN, id)).containsEntry("ref", W_TRICKY);

        rejected(create(WS_MAIN, values("id", nextId(), "ref", "x'; DELETE FROM t; --\n`y`")), "ref");
        assertThat(count(W_TGT, null)).as("参照先の行は消えていない").isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("NFR2.27: a 10,000 character value is saved and a value whose row has 10,001 characters is 409")
    void lengthBoundary() {
        int id = nextId();
        created(create(WS_MAIN, values("id", id, "long_ref", repeat("a", 10_000))));
        rejected(create(WS_MAIN, values("id", nextId(), "long_ref", repeat("b", 10_000))), "long_ref");
    }

    // ── すき間（T-G1・T-G2） ──

    @Test
    @DisplayName("T-G1: a target deleted between the check and the write is stopped by the foreign key (AC3.1.6)")
    void createGap() throws Exception {
        String value = "G1-" + nextId();
        admin("INSERT INTO %s" + W_TGT + " (id, v, l) VALUES (" + nextId() + ", '" + value + "', 'g')");
        int id = nextId();
        CountingDataSource reads = counting("targetDataSource");
        CountingDataSource writes = counting("targetWriteDataSource");
        reads.reset();
        writes.reset();
        StatementLog log = mysqlFamily() ? new StatementLog(sample.database()) : null;
        String mark = log == null ? null : log.mark();
        PausingTargetWriteAccess.Gate gate = pausing.arm();
        try {
            CompletableFuture<HttpResponse<String>> pending =
                    CompletableFuture.supplyAsync(() -> create(WS_MAIN, values("id", id, FK_COLUMN, value)));
            assertThat(gate.awaitArrival()).as("書き込みの入口に着いた").isTrue();
            assertThat(reads.inUse()).as("読み取りの接続は返してある").isZero();
            assertThat(counting("dataSource").inUse()).as("内部DB の接続を持ったまま待たない").isZero();
            assertThat(reads.borrows()).isEqualTo(1);
            assertThat(writes.borrows()).isZero();
            if (log != null) {
                assertThat(log.queriesSince(mark, sample.reader()))
                        .as("止める前に check の文が流れた")
                        .anyMatch(q -> q.contains(W_TGT));
            }
            admin("DELETE FROM %s" + W_TGT + " WHERE v = '" + value + "'");
            gate.release();

            HttpResponse<String> response = pending.get(30, TimeUnit.SECONDS);

            rejected(response, FK_COLUMN);
            assertThat(count(WS_MAIN, "id = " + id)).isZero();
            assertThat(pausing.timedOut()).isFalse();
        } finally {
            gate.release();
            if (log != null) {
                log.close();
            }
        }
    }

    @Test
    @DisplayName("T-G2: an unchanged reference whose target is replaced and deleted in the gap is stopped (AC3.2.5)")
    void updateGap() throws Exception {
        String x = "G2x-" + nextId();
        String y = "G2y-" + nextId();
        admin(
                "INSERT INTO %s" + W_TGT + " (id, v, l) VALUES (" + nextId() + ", '" + x + "', 'x')",
                "INSERT INTO %s" + W_TGT + " (id, v, l) VALUES (" + nextId() + ", '" + y + "', 'y')");
        int id = nextId();
        admin("INSERT INTO %s" + WS_MAIN + " (id, name, " + FK_COLUMN + ") VALUES (" + id + ", '前', '" + x + "')");
        CountingDataSource reads = counting("targetDataSource");
        CountingDataSource writes = counting("targetWriteDataSource");
        reads.reset();
        writes.reset();
        StatementLog log = mysqlFamily() ? new StatementLog(sample.database()) : null;
        String mark = log == null ? null : log.mark();
        PausingTargetWriteAccess.Gate gate = pausing.arm();
        try {
            // R-04: 参照でない別のカラムの新しい値と、更新前と同じ値の参照 X だけを送る。
            CompletableFuture<HttpResponse<String>> pending =
                    CompletableFuture.supplyAsync(() -> update(WS_MAIN, id, values("name", "後", FK_COLUMN, x)));
            assertThat(gate.awaitArrival()).isTrue();
            assertThat(reads.inUse()).isZero();
            assertThat(counting("dataSource").inUse()).isZero();
            assertThat(reads.borrows()).isEqualTo(1);
            assertThat(writes.borrows()).isZero();
            if (log != null) {
                List<String> queries = new ArrayList<>(log.queriesSince(mark, sample.reader()));
                assertThat(queries).as("更新前の行の detail だけ").anyMatch(q -> q.contains(WS_MAIN));
                assertThat(queries).as("check の文は流れていない").noneMatch(q -> q.contains(W_TGT));
            }
            admin(
                    "UPDATE %s" + WS_MAIN + " SET " + FK_COLUMN + " = '" + y + "' WHERE id = " + id,
                    "DELETE FROM %s" + W_TGT + " WHERE v = '" + x + "'");
            gate.release();

            HttpResponse<String> response = pending.get(30, TimeUnit.SECONDS);

            rejected(response, FK_COLUMN);
            assertThat(row(WS_MAIN, id)).containsEntry("name", "前").containsEntry(FK_COLUMN, y);
            assertThat(pausing.timedOut()).isFalse();
        } finally {
            gate.release();
            if (log != null) {
                log.close();
            }
        }
    }
}
