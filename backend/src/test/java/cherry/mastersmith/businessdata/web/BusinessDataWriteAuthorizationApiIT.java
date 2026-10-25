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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 業務データの書き込みの API 3本の認可の表と、判定の順・IDOR・一括代入（Intent 261016-business-data-views の U2、計画の Step 19、BR1.1〜
 * BR1.7、AC3.1.1〜AC3.1.4・AC3.1.6・AC3.1.9・AC3.1.10・AC3.5.1・AC3.5.2、team.md の「権限ごとの操作の可否」「IDOR と一括代入」）。
 * PostgreSQL のコンテナ。403 を確かめる利用者は、その操作に要る権限だけを欠き、ほかの権限（読み取りと、ほかの補助権限）は持つ。
 *
 * <p>拒んだ要求で状態が変わらないことは、アプリとは別の接続（管理者）で数え、対象DB に文が出ないことは、書き込みの接続を借りた回数
 * （{@link CountingDataSource}）が 0 であることで確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
@SuppressWarnings("unchecked")
class BusinessDataWriteAuthorizationApiIT {

    /** 主キーの無いテーブル（⑥ の確かめ）。 */
    private static final String NO_KEY = "nokey";

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private final AtomicInteger nextId = new AtomicInteger(100);

    private BusinessDataTestApp app;

    private CountingDataSource writes;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        sample.addObjects("CREATE TABLE %s" + NO_KEY + " (txt VARCHAR(20) NULL)");
        app = BusinessDataTestApp.start(
                dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[] {CountingDataSourceConfig.class});
        app.installDsl(
                sample.model(table(NO_KEY, false, List.of(), column("txt").build())));
        writes = app.bean("targetWriteDataSource", CountingDataSource.class);
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
        } finally {
            sample.stop();
        }
    }

    /** API。 */
    enum Endpoint {
        CREATE,
        UPDATE,
        DELETE
    }

    /** 主体。 */
    enum Subject {
        ANONYMOUS,
        SUSPENDED,
        LACKING,
        HOLDING
    }

    static Stream<Arguments> cases() {
        Stream.Builder<Arguments> rows = Stream.builder();
        for (Endpoint endpoint : Endpoint.values()) {
            rows.add(Arguments.of(endpoint, Subject.ANONYMOUS, 401, "AUTHENTICATION_REQUIRED"));
            rows.add(Arguments.of(endpoint, Subject.SUSPENDED, 401, "AUTHENTICATION_REQUIRED"));
            rows.add(Arguments.of(endpoint, Subject.LACKING, 403, "ACCESS_DENIED"));
            rows.add(Arguments.of(endpoint, Subject.HOLDING, endpoint == Endpoint.CREATE ? 201 : 200, null));
        }
        return rows.build();
    }

    /** {@code item} の権限を決めた設定（スキーマは READ）。 */
    private Map<PermissionTarget, PermissionValues> settings(MainPermission main, boolean create, boolean delete) {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(sample.schema()), new PermissionValues(MainPermission.READ, false, false));
        settings.put(PermissionTarget.table(sample.schema(), ITEM), new PermissionValues(main, create, delete));
        return settings;
    }

    /** その操作に要る権限だけを欠く設定（ほかの補助権限と読み取りは持つ）。 */
    private Map<PermissionTarget, PermissionValues> lacking(Endpoint endpoint) {
        return switch (endpoint) {
            case CREATE -> settings(MainPermission.FULL, false, true);
            case UPDATE -> settings(MainPermission.READ, true, true);
            case DELETE -> settings(MainPermission.FULL, true, false);
        };
    }

    private String insertRow() {
        String id = Integer.toString(nextId.getAndIncrement());
        sample.database()
                .execute("INSERT INTO " + sample.database().q(sample.schema()) + "." + ITEM + " (id, name) VALUES ("
                        + id + ", '認可')");
        return id;
    }

    private Map<String, Object> body(Actor member, String table) {
        Map<String, Object> body = sample.body(table);
        body.put("definitionId", member == null ? Map.of("dslHash", "x") : definitionOf(member));
        return body;
    }

    private Map<String, Object> definitionOf(Actor member) {
        return app.definitionId(member.token(), sample.schema(), ITEM);
    }

    private HttpResponse<String> send(Endpoint endpoint, String token, Map<String, Object> definitionId, String id) {
        Map<String, Object> body = sample.body(ITEM);
        body.put("definitionId", definitionId);
        return switch (endpoint) {
            case CREATE -> {
                body.put("values", Map.of("id", id, "name", "作成"));
                yield app.post(BusinessDataPaths.ROWS, token, body);
            }
            case UPDATE -> {
                body.put("key", Map.of("id", id));
                body.put("values", Map.of("name", "更新"));
                yield app.patch(BusinessDataPaths.ROWS, token, body);
            }
            case DELETE -> {
                body.put("key", Map.of("id", id));
                yield app.post(BusinessDataPaths.DELETE, token, body);
            }
        };
    }

    private long rows(String where) {
        return sample.count(ITEM, where);
    }

    @ParameterizedTest(name = "{0} by {1} gives {2}")
    @MethodSource("cases")
    @DisplayName("each write API is 401 without login or when suspended, 403 without its permission and 2xx with it")
    void authorization(Endpoint endpoint, Subject subject, int status, String code) {
        Actor member = app.reader(subject == Subject.LACKING ? lacking(endpoint) : sample.writeSettings());
        Map<String, Object> definitionId = definitionOf(member);
        if (subject == Subject.SUSPENDED) {
            app.roleActors().suspend(member.userId());
        }
        String token = subject == Subject.ANONYMOUS ? null : member.token();
        String id = endpoint == Endpoint.CREATE ? Integer.toString(nextId.getAndIncrement()) : insertRow();
        writes.reset();

        HttpResponse<String> response = send(endpoint, token, definitionId, id);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        if (code != null) {
            assertThat(HttpTestClient.json(response)).containsEntry("code", code);
            assertThat(writes.borrows()).as("拒んだ要求は書き込みの接続を借りない").isZero();
        }
        long expected =
                switch (endpoint) {
                    case CREATE -> status == 201 ? 1 : 0;
                    case UPDATE -> status == 200 ? 1 : 0;
                    case DELETE -> status == 200 ? 0 : 1;
                };
        String where = endpoint == Endpoint.UPDATE ? "id = " + id + " AND name = '更新'" : "id = " + id;
        assertThat(rows(where)).as("別の接続で読み直す").isEqualTo(expected);
    }

    @Test
    @DisplayName("switching the work role and changing the assignment switch 403 and 2xx on the very next request")
    void switchesOnTheNextRequest() {
        long writeRole = app.role(sample.writeSettings());
        long readRole = app.role(settings(MainPermission.READ, false, false));
        Actor member = app.reader(settings(MainPermission.READ, false, false));
        app.fixtures().assignUser(writeRole, member.userId());
        app.fixtures().assignUser(readRole, member.userId());

        app.fixtures().selectWorkRole(member.userId(), writeRole);
        Map<String, Object> writeDefinition = definitionOf(member);
        assertThat(send(Endpoint.UPDATE, member.token(), writeDefinition, insertRow())
                        .statusCode())
                .isEqualTo(200);
        app.fixtures().selectWorkRole(member.userId(), readRole);
        assertThat(send(Endpoint.UPDATE, member.token(), definitionOf(member), insertRow())
                        .statusCode())
                .as("作業ロールを切り替えた直後")
                .isEqualTo(403);
        app.fixtures().selectWorkRole(member.userId(), writeRole);
        assertThat(send(Endpoint.DELETE, member.token(), writeDefinition, insertRow())
                        .statusCode())
                .isEqualTo(200);

        JdbcTemplate jdbc = app.bean(JdbcTemplate.class);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ? AND role_id = ?", member.userId(), writeRole);
        // 作業ロールが変わるため、前の定義の識別は ⑤ で 409 になる。読み直した定義の識別で、⑦ の 403 を確かめる。
        assertThat(send(Endpoint.CREATE, member.token(), writeDefinition, Integer.toString(nextId.getAndIncrement()))
                        .statusCode())
                .as("割り当てを外した直後（前の定義の識別）")
                .isEqualTo(409);
        assertThat(send(
                                Endpoint.CREATE,
                                member.token(),
                                definitionOf(member),
                                Integer.toString(nextId.getAndIncrement()))
                        .statusCode())
                .as("割り当てを外した直後")
                .isEqualTo(403);
        app.fixtures().assignUser(writeRole, member.userId());
        app.fixtures().selectWorkRole(member.userId(), writeRole);
        assertThat(send(Endpoint.CREATE, member.token(), writeDefinition, Integer.toString(nextId.getAndIncrement()))
                        .statusCode())
                .as("割り当てた直後")
                .isEqualTo(201);
    }

    @Test
    @DisplayName("the order of checks: NONE is 403 before a missing row, the key rule is 409 before 403, old is 409")
    void orderOfChecks() {
        Actor none = app.reader(settings(MainPermission.NONE, true, true));
        Actor reader = app.reader(settings(MainPermission.READ, false, false));
        Actor writer = app.reader(sample.writeSettings());
        writes.reset();

        HttpResponse<String> noneMissing = send(Endpoint.UPDATE, none.token(), Map.of("dslHash", "x"), "99999");
        Map<String, Object> noKeyBody = body(reader, NO_KEY);
        noKeyBody.put("key", Map.of("txt", "a"));
        noKeyBody.put("values", Map.of("txt", "b"));
        HttpResponse<String> structural = app.patch(BusinessDataPaths.ROWS, reader.token(), noKeyBody);
        Map<String, Object> stale = new LinkedHashMap<>(definitionOf(writer));
        stale.put("dslHash", "f".repeat(64));
        String staleId = insertRow();
        HttpResponse<String> old = send(Endpoint.UPDATE, writer.token(), stale, staleId);
        Map<String, Object> otherRole = new LinkedHashMap<>(definitionOf(writer));
        otherRole.put("workRoleId", 999_999L);
        HttpResponse<String> swappedRole = send(Endpoint.DELETE, writer.token(), otherRole, insertRow());
        HttpResponse<String> missingDefinition = send(Endpoint.CREATE, writer.token(), null, "77777");

        assertProblem(noneMissing, 403, "ACCESS_DENIED");
        assertProblem(structural, 409, "BUSINESS_ROW_OPERATION_NOT_ALLOWED");
        assertProblem(old, 409, "BUSINESS_DEFINITION_CHANGED");
        assertProblem(swappedRole, 409, "BUSINESS_DEFINITION_CHANGED");
        assertProblem(missingDefinition, 409, "BUSINESS_DEFINITION_CHANGED");
        assertThat(writes.borrows()).as("どれも対象DB に触れない").isZero();
        assertThat(rows("id = 77777")).isZero();
        assertThat(rows("id = " + staleId + " AND name = '更新'"))
                .as("古い定義の更新は反映されない")
                .isZero();
    }

    @Test
    @DisplayName("unknown schemas and tables are 404, unwritable columns are 400 NOT_ALLOWED and nothing changes")
    void idorAndMassAssignment() {
        Actor writer = app.reader(sample.writeSettings());
        Map<String, Object> definitionId = definitionOf(writer);
        String id = insertRow();
        writes.reset();

        Map<String, Object> otherSchema = new LinkedHashMap<>(body(writer, ITEM));
        otherSchema.put("schema", "public");
        otherSchema.put("values", Map.of("id", "88888", "name", "x"));
        Map<String, Object> otherTable = new LinkedHashMap<>(body(writer, ITEM));
        otherTable.put("table", "users");
        otherTable.put("values", Map.of("id", "88888", "name", "x"));
        Map<String, Object> keyChange = sample.body(ITEM);
        keyChange.put("definitionId", definitionId);
        keyChange.put("key", Map.of("id", id));
        keyChange.put("values", Map.of("id", "88888", "name", "書き換え"));
        Map<String, Object> unknownColumn = sample.body(ITEM);
        unknownColumn.put("definitionId", definitionId);
        unknownColumn.put("key", Map.of("id", id));
        unknownColumn.put("values", Map.of("name", "書き換え", "owner_id", "1"));

        assertProblem(app.post(BusinessDataPaths.ROWS, writer.token(), otherSchema), 404, "BUSINESS_TABLE_NOT_FOUND");
        assertProblem(app.post(BusinessDataPaths.ROWS, writer.token(), otherTable), 404, "BUSINESS_TABLE_NOT_FOUND");
        Map<String, Object> keyProblem =
                assertProblem(app.patch(BusinessDataPaths.ROWS, writer.token(), keyChange), 400, "VALIDATION_FAILED");
        Map<String, Object> columnProblem = assertProblem(
                app.patch(BusinessDataPaths.ROWS, writer.token(), unknownColumn), 400, "VALIDATION_FAILED");
        assertThat((List<Map<String, Object>>) keyProblem.get("fieldErrors"))
                .containsExactly(Map.of("field", "id", "reason", "NOT_ALLOWED"));
        assertThat((List<Map<String, Object>>) columnProblem.get("fieldErrors"))
                .containsExactly(Map.of("field", "", "reason", "NOT_ALLOWED"));
        assertThat(writes.borrows()).as("対象DB に文を出さない").isZero();
        assertThat(rows("id = 88888")).isZero();
        assertThat(rows("id = " + id + " AND name = '認可'")).as("状態は変わらない").isEqualTo(1);

        assertProblem(send(Endpoint.DELETE, writer.token(), definitionId, "424242"), 404, "BUSINESS_ROW_NOT_FOUND");
        assertProblem(send(Endpoint.UPDATE, writer.token(), definitionId, "424242"), 404, "BUSINESS_ROW_NOT_FOUND");
        assertThat(rows("id = 424242")).isZero();
    }

    private static Map<String, Object> assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        Map<String, Object> json = HttpTestClient.json(response);
        assertThat(json).containsEntry("code", code);
        return json;
    }
}
