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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_API;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.TGT;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * 参照先の検索 API の同じ応答と判定の順（Intent 261029-reference-lookup-cleanup の U1、Step 21、BR1.2〜BR1.4・BR5.2・BR5.3・BR6.2・
 * BR6.4、NFR1.2、team.md の他テーブル参照の必須のテスト (a)(b)）。PostgreSQL のコンテナ。
 *
 * <p>403 の各場合（権限の欠け・DSL に無い名前・参照でないカラム・REFERENCE に検索・{@code searchFields} に無いカラム・型として読めない
 * ラベルの値・行が無いラベル・DSL が無い）の状態コード・{@code code}・本文の構造とメッセージが同じで、行の無いラベルを除いて対象DB の接続を
 * 1回も借りない（SQL が流れない）ことを確かめる。本文の知らない項目（参照先の名前など）は結果を変えない。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceSameResponseIT {

    /**
     * 整数のカラム {@code n} として読めない検索の値で、応答の本文に返らないことを確かめる目印。
     *
     * <p>16進に無い文字（{@code z}・{@code q}・{@code -} など）を含めるため、応答の乱数のトレースID（32 桁の16進）や type の URL の
     * 番号に偶然に当たらない（前の {@code "abc"} は CI の run 41161783903 でトレースID {@code …63541abc} に当たって落ちた）。
     */
    private static final String NOT_A_NUMBER_MARK = "zq-sentinel-not-a-number";

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private ReferenceTables tables;

    private Actor member;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        tables = new ReferenceTables(sample);
        tables.createApi();
        app = BusinessDataTestApp.start(
                dir.resolve("main"), sample.target(), new Class<?>[] {CountingDataSourceConfig.class});
        app.installDsl(sample.model(tables.apiTables()));
        member = app.reader(tables.apiSettings(Map.of()));
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

    private CountingDataSource target() {
        return (CountingDataSource) app.bean("targetDataSource", DataSource.class);
    }

    private Map<String, Object> searchBody(String schema, String table, String column, List<Map<String, Object>> c) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", schema);
        body.put("table", table);
        body.put("column", column);
        body.put("conditions", c);
        body.put("page", 1);
        return body;
    }

    private Map<String, Object> labelBody(String schema, String table, String column, String value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", schema);
        body.put("table", table);
        body.put("column", column);
        body.put("value", value);
        return body;
    }

    private static List<Map<String, Object>> cond(String column, String value) {
        return List.of(Map.of("column", column, "value", value));
    }

    private HttpResponse<String> post(Actor actor, Map<String, Object> body) {
        return app.post(BusinessDataPaths.REFERENCE_ROWS, actor.token(), body);
    }

    /** 毎回違うトレースID を除いた本文。 */
    private static Map<String, Object> stable(HttpResponse<String> response) {
        Map<String, Object> json = new LinkedHashMap<>(HttpTestClient.json(response));
        json.remove("traceId");
        return json;
    }

    @Test
    @DisplayName("every 403 case gives the same status, code, body and message and borrows no target connection (b)")
    void every403IsTheSame() {
        String schema = sample.schema();
        Actor readOnly = app.reader(tables.apiSettings(Map.of(
                PermissionTarget.column(schema, SRC_API, "pick"),
                new PermissionValues(MainPermission.READ, null, null))));
        Actor noTarget = app.reader(tables.apiSettings(
                Map.of(PermissionTarget.table(schema, TGT), new PermissionValues(MainPermission.NONE, null, null))));
        CountingDataSource counter = target();
        counter.reset();

        List<HttpResponse<String>> denied = new ArrayList<>();
        denied.add(post(readOnly, searchBody(schema, SRC_API, "pick", cond("s", "S1"))));
        denied.add(post(noTarget, labelBody(schema, SRC_API, "pick", "V1")));
        denied.add(post(member, searchBody("no_such", SRC_API, "pick", cond("s", "S1"))));
        denied.add(post(member, searchBody(schema, "no'such;table", "pick", cond("s", "S1"))));
        denied.add(post(member, labelBody(schema, SRC_API, "no_such_column", "V1")));
        denied.add(post(member, labelBody(schema, SRC_API, "memo", "V1")));
        denied.add(post(member, searchBody(schema, SRC_API, "ref", cond("v", "V1"))));
        denied.add(post(member, searchBody(schema, SRC_API, "pick", cond("x", "X1"))));
        denied.add(post(member, searchBody(schema, SRC_API, "pick", cond("l", "L1"))));
        denied.add(post(member, labelBody(schema, SRC_API, "refnum", "abc")));
        int noTargetQuery = denied.size();
        long borrowedBefore = counter.borrows();
        denied.add(post(member, labelBody(schema, SRC_API, "ref", "V9")));
        long borrowedForMissingRow = counter.borrows() - borrowedBefore;
        app.installDsl(null);
        try {
            denied.add(post(member, labelBody(schema, SRC_API, "ref", "V1")));
        } finally {
            app.installDsl(sample.model(tables.apiTables()));
        }

        Map<String, Object> first = stable(denied.get(0));
        assertThat(first)
                .containsEntry("status", 403)
                .containsEntry("code", "ACCESS_DENIED")
                .doesNotContainKey("fieldErrors");
        for (HttpResponse<String> response : denied) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
            assertThat(stable(response)).as(response.body()).isEqualTo(first);
        }
        assertThat(borrowedBefore)
                .as("行の無いラベルの前の %d 件は対象DB を借りない", noTargetQuery)
                .isZero();
        assertThat(borrowedForMissingRow).as("行の無いラベルだけは読んでから 403").isEqualTo(1);
    }

    @Test
    @DisplayName("the order of the checks: format 400 before names 403, names 403 before the EQUALS type 400")
    void orderOfTheChecks() {
        String schema = sample.schema();
        Map<String, Object> bothWithUnknownTable = labelBody(schema, "no_such", "pick", "V1");
        bothWithUnknownTable.put("conditions", cond("s", "S1"));
        HttpResponse<String> formatFirst = post(member, bothWithUnknownTable);
        HttpResponse<String> tooLongUnknown =
                post(member, labelBody("no_such", "no_such", "no_such", "x".repeat(1_001)));
        List<Map<String, Object>> unknownAndBadType =
                List.of(Map.of("column", "no_such", "value", "x"), Map.of("column", "n", "value", NOT_A_NUMBER_MARK));
        HttpResponse<String> namesBeforeType = post(member, searchBody(schema, SRC_API, "pick", unknownAndBadType));
        HttpResponse<String> badType = post(member, searchBody(schema, SRC_API, "pick", cond("n", NOT_A_NUMBER_MARK)));

        for (HttpResponse<String> response : List.of(formatFirst, tooLongUnknown)) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(HttpTestClient.json(response))
                    .containsEntry("code", "VALIDATION_FAILED")
                    .doesNotContainKey("fieldErrors");
        }
        assertThat(namesBeforeType.statusCode()).as(namesBeforeType.body()).isEqualTo(403);
        assertThat(badType.statusCode()).as(badType.body()).isEqualTo(400);
        assertThat(HttpTestClient.json(badType))
                .containsEntry("fieldErrors", List.of(Map.of("field", "n", "reason", "INVALID_VALUE")));
        assertThat(badType.body()).doesNotContain(NOT_A_NUMBER_MARK);
    }

    @Test
    @DisplayName("unknown body fields such as a forged target table do not change the answer")
    void unknownFieldsAreIgnored() {
        String schema = sample.schema();
        Map<String, Object> plain = searchBody(schema, SRC_API, "pick", cond("s", "S1"));
        Map<String, Object> forged = new LinkedHashMap<>(plain);
        forged.put("targetTable", "tgtpw");
        forged.put("valueColumn", "l");
        forged.put("labelColumn", "x");

        HttpResponse<String> a = post(member, plain);
        HttpResponse<String> b = post(member, forged);
        HttpResponse<String> label = post(member, labelBody(schema, SRC_API, "refnum", "1"));

        assertThat(a.statusCode()).as(a.body()).isEqualTo(200);
        assertThat(HttpTestClient.json(b)).isEqualTo(HttpTestClient.json(a));
        assertThat(HttpTestClient.json(a))
                .containsEntry("hasNext", false)
                .containsEntry("rows", List.of(Map.of("value", "V1", "label", "L1", "cells", List.of("L1", "X1"))));
        assertThat(HttpTestClient.json(label))
                .containsEntry("rows", List.of(Map.of("value", "1", "label", "L1", "cells", List.of())));
    }
}
