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
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_NUM;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_TGT;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.businessdata.testsupport.StatementLog;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 保存前の参照の確かめの時間切れ・失敗（Intent 261029-reference-lookup-cleanup の U2、計画の Step 10、reliability-design.md 2節・6節の
 * T-T1・T-F1・T-F2、NFR3.24〜NFR3.26、AC3.1.8・AC3.5.3）。3種類の対象DB。
 *
 * <ul>
 *   <li>T-T1: 眠る関数を含むビューを参照先にした作成・更新は、文の上限で 503 {@code BUSINESS_TARGET_DB_TIMEOUT}（保存しない・書き込みを
 *       借りない）。U1 のコードのレビュー（Minor）のとおり、応答しない対象DB は借りる段で止まるため TIMEOUT の確かめには使わない
 *   <li>T-F1: 接続を拒む番号へ向けた対象DB は 503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}、DSL にあり対象DB に無いカラムは 409
 *       {@code BUSINESS_TARGET_DB_MISMATCH}
 *   <li>T-F2: 参照 3 つの2つ目が時間切れなら、3つ目の問い合わせを流さない（MySQL・MariaDB は一般ログで確かめる）
 * </ul>
 *
 * <p>どれも応答に接続先・ユーザー名・例外の文が無い。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceWriteTimeoutIT {

    /** 眠るビュー（参照先）。 */
    private static final String SLOW = "wt_slow";

    /** 参照元（r1 → 小さな参照先、a → 眠るビュー、r3 → 整数の参照先。DSL の並びはこの順）。 */
    private static final String SRC = "ws_slow";

    /** 食い違いの参照元（m → other の ghost）。 */
    private static final String MISMATCH = "ws_mm";

    /** DB の種類ごとの環境。 */
    private record Env(
            BusinessDataSampleSchema sample,
            BusinessDataTestApp app,
            Actor member,
            BusinessDataTestApp refused,
            Actor refusedMember) {}

    private final Map<DatabaseProduct, Env> envs = new EnumMap<>(DatabaseProduct.class);

    private final List<AutoCloseable> closeables = new ArrayList<>();

    private final AtomicInteger ids = new AtomicInteger(4_000);

    @BeforeAll
    void start(@TempDir Path dir) {
        for (DatabaseProduct product : DatabaseProduct.values()) {
            envs.put(product, env(dir, product));
        }
    }

    @AfterAll
    void stop() throws Exception {
        for (int i = closeables.size() - 1; i >= 0; i--) {
            closeables.get(i).close();
        }
    }

    private static DslColumn ref(String name, String dbType, String table, String value, String label) {
        return column(name)
                .dbType(dbType)
                .form(FormPart.SELECT)
                .options(new OptionSource(
                        OptionSourceKind.REFERENCE, List.of(), table, value, label, List.of(), List.of()))
                .build();
    }

    private static DslModel model(BusinessDataSampleSchema sample, ReferenceTables tables) {
        boolean postgres = sample.database().product() == DatabaseProduct.POSTGRESQL;
        String integer = postgres ? "int4" : "int";
        String bigint = postgres ? "int8" : "bigint";
        List<DslTable> extra = new ArrayList<>(List.of(tables.writeTables()));
        extra.add(table(
                SLOW,
                true,
                List.of("id"),
                column("id").dbType(integer).build(),
                column("v").build(),
                column("l").build()));
        extra.add(table(
                SRC,
                false,
                List.of("id"),
                column("id").dbType(integer).build(),
                ref("r1", "varchar", W_TGT, "v", "l"),
                ref("a", "varchar", SLOW, "v", "l"),
                ref("r3", bigint, W_NUM, "v", "l")));
        extra.add(table(
                MISMATCH,
                false,
                List.of("id"),
                column("id").dbType(integer).build(),
                ref("m", "varchar", BusinessDataSampleSchema.OTHER, "ghost", "title")));
        extra.add(table(
                BusinessDataSampleSchema.OTHER,
                false,
                List.of("id"),
                column("id").dbType(integer).build(),
                column("title").build(),
                column("ghost").build()));
        return sample.model(extra.toArray(DslTable[]::new));
    }

    private Env env(Path dir, DatabaseProduct product) {
        BusinessDataSampleSchema sample = new BusinessDataSampleSchema(product);
        sample.start();
        closeables.add(sample::stop);
        ReferenceTables tables = new ReferenceTables(sample);
        tables.createWrite();
        boolean postgres = product == DatabaseProduct.POSTGRESQL;
        String sleepColumn = postgres ? "pg_sleep(8)::text" : "CAST(SLEEP(8) AS CHAR)";
        sample.addObjects(
                "CREATE VIEW %s" + SLOW + " AS SELECT id, " + sleepColumn + " AS v, l FROM %s" + W_TGT,
                "CREATE TABLE %s" + SRC + " (id INTEGER NOT NULL PRIMARY KEY, r1 VARCHAR(40) NULL, a VARCHAR(40) NULL,"
                        + " r3 BIGINT NULL)",
                "CREATE TABLE %s" + MISMATCH + " (id INTEGER NOT NULL PRIMARY KEY, m VARCHAR(40) NULL)");
        DslModel model = model(sample, tables);
        BusinessDataTestApp app = BusinessDataTestApp.start(
                dir.resolve(product.name() + "-main"), sample.target(), sample.writer(), new Class<?>[] {
                    CountingDataSourceConfig.class
                });
        closeables.add(app);
        app.installDsl(model);
        BusinessDataTestApp.Target target = sample.target();
        // 接続を拒む番号（ホストの 1 番）へ向けたアプリ（T-F1 の UNAVAILABLE）。
        BusinessDataTestApp refused = BusinessDataTestApp.start(
                dir.resolve(product.name() + "-refused"),
                new BusinessDataTestApp.Target(
                        target.product(),
                        target.host(),
                        1,
                        target.database(),
                        target.schema(),
                        target.user(),
                        target.password()),
                sample.writer(),
                new Class<?>[0]);
        closeables.add(refused);
        refused.installDsl(model);
        return new Env(
                sample,
                app,
                app.reader(tables.writeSettings(Map.of())),
                refused,
                refused.reader(tables.writeSettings(Map.of())));
    }

    private static Map<String, Object> body(
            BusinessDataTestApp app, Actor actor, BusinessDataSampleSchema sample, String table) {
        Map<String, Object> body = sample.body(table);
        body.put("definitionId", app.definitionId(actor.token(), sample.schema(), table));
        return body;
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code, Env env) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code).doesNotContainKey("fieldErrors");
        BusinessDataTestApp.Target target = env.sample().target();
        assertThat(response.body())
                .doesNotContain("Exception")
                .doesNotContain(target.host() + ":" + target.port())
                .doesNotContain(target.user())
                .doesNotContain(env.sample().writer().user())
                .doesNotContain("jdbc:");
    }

    private CountingDataSource writes(Env env) {
        return (CountingDataSource) env.app().bean("targetWriteDataSource", DataSource.class);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("T-T1: a statement timeout while checking is 503 TIMEOUT for create and update and saves nothing")
    void timeoutSavesNothing(DatabaseProduct product) {
        Env env = envs.get(product);
        String id = Integer.toString(ids.incrementAndGet());
        writes(env).reset();
        Map<String, Object> create = body(env.app(), env.member(), env.sample(), SRC);
        create.put("values", values("id", id, "a", "x"));

        assertProblem(
                env.app().post(BusinessDataPaths.ROWS, env.member().token(), create),
                503,
                "BUSINESS_TARGET_DB_TIMEOUT",
                env);
        assertThat(env.sample().count(SRC, "id = " + id)).isZero();

        String base = Integer.toString(ids.incrementAndGet());
        env.sample().addObjects("INSERT INTO %s" + SRC + " (id, a) VALUES (" + base + ", 'old')");
        Map<String, Object> update = body(env.app(), env.member(), env.sample(), SRC);
        update.put("key", Map.of("id", base));
        update.put("values", values("a", "new"));
        assertProblem(
                env.app().patch(BusinessDataPaths.ROWS, env.member().token(), update),
                503,
                "BUSINESS_TARGET_DB_TIMEOUT",
                env);
        assertThat(env.sample().count(SRC, "id = " + base + " AND a = 'old'")).isEqualTo(1);
        assertThat(writes(env).borrows()).as("書き込みを借りない").isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("T-F2: a timeout of the second check stops before the third statement")
    void timeoutStopsTheRest(DatabaseProduct product) {
        Env env = envs.get(product);
        String id = Integer.toString(ids.incrementAndGet());
        Map<String, Object> create = body(env.app(), env.member(), env.sample(), SRC);
        create.put("values", values("id", id, "r1", "V1", "a", "x", "r3", "7"));
        StatementLog log = product == DatabaseProduct.POSTGRESQL
                ? null
                : new StatementLog(env.sample().database());
        try {
            String mark = log == null ? null : log.mark();

            assertProblem(
                    env.app().post(BusinessDataPaths.ROWS, env.member().token(), create),
                    503,
                    "BUSINESS_TARGET_DB_TIMEOUT",
                    env);

            if (log != null) {
                List<String> queries = log.queriesSince(mark, env.sample().reader());
                assertThat(queries).as("1つ目と2つ目は流れた").anyMatch(q -> q.contains(W_TGT));
                assertThat(queries).anyMatch(q -> q.contains(SLOW));
                assertThat(queries).as("3つ目は流れない").noneMatch(q -> q.contains(W_NUM));
            }
        } finally {
            if (log != null) {
                log.close();
            }
        }
        assertThat(env.sample().count(SRC, "id = " + id)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("T-F1: a refused connection is 503 UNAVAILABLE and a missing column is 409 MISMATCH")
    void failures(DatabaseProduct product) {
        Env env = envs.get(product);
        Map<String, Object> refused = body(env.refused(), env.refusedMember(), env.sample(), SRC);
        refused.put("values", values("id", Integer.toString(ids.incrementAndGet()), "r1", "V1"));
        assertProblem(
                env.refused().post(BusinessDataPaths.ROWS, env.refusedMember().token(), refused),
                503,
                "BUSINESS_TARGET_DB_UNAVAILABLE",
                env);

        String id = Integer.toString(ids.incrementAndGet());
        Map<String, Object> mismatch = body(env.app(), env.member(), env.sample(), MISMATCH);
        mismatch.put("values", values("id", id, "m", "見本"));
        assertProblem(
                env.app().post(BusinessDataPaths.ROWS, env.member().token(), mismatch),
                409,
                "BUSINESS_TARGET_DB_MISMATCH",
                env);
        assertThat(env.sample().count(MISMATCH, "id = " + id)).isZero();
    }
}
