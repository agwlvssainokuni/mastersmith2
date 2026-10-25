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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 適用中の DSL に無い名前（スキーマ・テーブル・並べ替えの列・検索の列・主キーの列）を送った読み取りの要求は、対象DB に一度も届かずに
 * 拒否される（Intent 261025-deps-perf-carryover の FR3.7 R-02。前の Intent のコードのレビューで「認可の表に、DSL に無い名前の確かめが
 * 無い」と指摘された欠けを足す。本番のコードは変えず、既に正しい動作を確かめる）。3種類の対象DB で流す。
 *
 * <ul>
 *   <li>DSL に無いスキーマ・テーブル: 404 {@code BUSINESS_TABLE_NOT_FOUND}（3本の API のすべて）
 *   <li>DSL が適用されていない: 404 {@code BUSINESS_DSL_NOT_APPLIED}（3本の API のすべて）
 *   <li>DSL に無い並べ替えの列・検索の列・主キーの列: 400 {@code VALIDATION_FAILED}
 * </ul>
 *
 * <p>名前には {@code '}・{@code ;}・{@code "}・バッククォート・改行・{@code --} を含める。「対象DB に問い合わせが出ない」は、対象DB の
 * 接続を貸す部品（{@link CountingDataSource}）が借りた回数 0 で確かめる（文を作る部品は接続を借りてからしか文を作れないため、借りない
 * ことは文を出さないことと同じ）。最後に、正しい要求で借りた回数が 1 になる（数える部品が働いている）ことも確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataUnknownNamesIT {

    /** 引用符・{@code ;}・{@code --}・バッククォート・改行を含む、DSL に無い名前。 */
    private static final List<String> NAMES = List.of(
            "no_such",
            "no'such",
            "no;such",
            "no\"such",
            "no`such",
            "no\nsuch",
            "no--such",
            "x'; DROP TABLE item; --",
            "../item",
            "ITEM ");

    /** DB の種類ごとの環境。 */
    private record Env(
            BusinessDataSampleSchema sample,
            BusinessDataTestApp app,
            Actor member,
            BusinessDataTestApp noDslApp,
            Actor noDslMember) {}

    private final Map<DatabaseProduct, Env> envs = new EnumMap<>(DatabaseProduct.class);

    @BeforeAll
    void start(@TempDir Path dir) {
        for (DatabaseProduct product : DatabaseProduct.values()) {
            BusinessDataSampleSchema sample = new BusinessDataSampleSchema(product);
            sample.start();
            BusinessDataTestApp app =
                    BusinessDataTestApp.start(dir.resolve(product.name() + "-main"), sample.target(), new Class<?>[] {
                        CountingDataSourceConfig.class
                    });
            app.installDsl(sample.model());
            BusinessDataTestApp noDslApp =
                    BusinessDataTestApp.start(dir.resolve(product.name() + "-nodsl"), sample.target());
            envs.put(
                    product,
                    new Env(
                            sample,
                            app,
                            app.reader(sample.settings(MainPermission.READ)),
                            noDslApp,
                            noDslApp.reader(sample.settings(MainPermission.READ))));
        }
    }

    @AfterAll
    void stop() {
        for (Env env : envs.values()) {
            try {
                env.noDslApp().close();
                env.app().close();
            } finally {
                env.sample().stop();
            }
        }
    }

    private static CountingDataSource target(BusinessDataTestApp app) {
        return (CountingDataSource) app.bean("targetDataSource", DataSource.class);
    }

    private static Map<String, Object> body(String schema, String table, Object... extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", schema);
        body.put("table", table);
        for (int i = 0; i < extra.length; i += 2) {
            body.put((String) extra[i], extra[i + 1]);
        }
        return body;
    }

    /** 3本の API に同じ表・スキーマの名前で送る。 */
    private static List<HttpResponse<String>> threeApis(
            BusinessDataTestApp app, Actor member, String schema, String table) {
        return List.of(
                app.post(BusinessDataPaths.DEFINITION, member.token(), body(schema, table)),
                app.post(BusinessDataPaths.SEARCH, member.token(), body(schema, table, "page", 1, "size", 20)),
                app.post(BusinessDataPaths.GET, member.token(), body(schema, table, "key", Map.of("id", "1"))));
    }

    private static void assertProblems(List<HttpResponse<String>> responses, int status, String code) {
        for (HttpResponse<String> response : responses) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
            assertThat(HttpTestClient.json(response)).containsEntry("code", code);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("names outside the applied DSL are 404 or 400 and never reach the target database: a schema, a table,"
            + " a sort column, a search column and a key column")
    void unknownNamesAreRejectedBeforeTheTargetDatabase(DatabaseProduct product) {
        Env env = envs.get(product);
        String schema = env.sample().schema();
        CountingDataSource counter = target(env.app());
        counter.reset();

        for (String name : NAMES) {
            assertProblems(
                    threeApis(env.app(), env.member(), name, BusinessDataSampleSchema.ITEM),
                    404,
                    "BUSINESS_TABLE_NOT_FOUND");
            assertProblems(threeApis(env.app(), env.member(), schema, name), 404, "BUSINESS_TABLE_NOT_FOUND");
            HttpResponse<String> sort = env.app()
                    .post(
                            BusinessDataPaths.SEARCH,
                            env.member().token(),
                            body(
                                    schema,
                                    BusinessDataSampleSchema.ITEM,
                                    "sort",
                                    Map.of("column", name, "direction", "ASC")));
            HttpResponse<String> search = env.app()
                    .post(
                            BusinessDataPaths.SEARCH,
                            env.member().token(),
                            body(
                                    schema,
                                    BusinessDataSampleSchema.ITEM,
                                    "conditions",
                                    List.of(Map.of("column", name, "value", "x"))));
            HttpResponse<String> key = env.app()
                    .post(
                            BusinessDataPaths.GET,
                            env.member().token(),
                            body(schema, BusinessDataSampleSchema.ITEM, "key", Map.of("id", "1", name, "1")));
            assertProblems(List.of(sort, search, key), 400, "VALIDATION_FAILED");
        }
        assertThat(counter.borrows()).as("対象DB の接続を一度も借りていない（文を出していない）").isZero();

        HttpResponse<String> valid = env.app()
                .post(
                        BusinessDataPaths.SEARCH,
                        env.member().token(),
                        body(schema, BusinessDataSampleSchema.ITEM, "page", 1, "size", 20));
        assertThat(valid.statusCode()).as(valid.body()).isEqualTo(200);
        assertThat(counter.borrows()).as("正しい要求では借りる（数える部品が働いている）").isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("without an applied DSL every read API is 404 DSL_NOT_APPLIED whatever the names are")
    void noDslIsRejected(DatabaseProduct product) {
        Env env = envs.get(product);

        for (String name : List.of(BusinessDataSampleSchema.ITEM, "no'such;table")) {
            assertProblems(
                    threeApis(env.noDslApp(), env.noDslMember(), env.sample().schema(), name),
                    404,
                    "BUSINESS_DSL_NOT_APPLIED");
        }
    }
}
