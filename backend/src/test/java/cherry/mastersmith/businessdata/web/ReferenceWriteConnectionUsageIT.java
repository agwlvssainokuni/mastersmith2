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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_FIVE;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_MAIN;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_REQ_TWO;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_MANY;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.businessdata.testsupport.StatementLog;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 保存前の参照の確かめが1要求で借りる接続の数（Intent 261029-reference-lookup-cleanup の U2、計画の Step 10、(j)、NFR1.21・NFR1.25・
 * NFR3.21、AC3.2.7・AC3.5.1・AC3.5.2）。PostgreSQL・MySQL・MariaDB のコンテナ。{@link CountingDataSourceConfig} で、内部DB・読み取り・
 * 書き込みの借りた回数と、書き込みを借りた時点の読み取りの使用中（{@code peerInUseAtBorrow}）を決定的に数える。
 *
 * <p>MariaDB と内部DB の借りた回数は Build and Test で足した（U2 のコードのレビューの R-02、team.md の (j)）。ここで流す要求は、どれも
 * 監査に残る結果（作成・更新・削除の成功と、参照の拒否）で、内部DB を借りる回数は参照の数・成功か拒否か・DB の種類によらず 3 回
 * （実測）。監査の書き込みは確定の後の同期の受け取りで要求のスレッドの中で済むため、応答の後に数えれば決まった値になる。内部DB は
 * 同時に1本までしか借りないことと、応答の後に返していることも確かめる。読み取りと書き込みを組にして数えるため、内部DB は相手を
 * 持たせずに数える（内部DB と対象DB を同時に借りないことは {@code AbstractReferenceWriteIT} の T-G1・T-G2 が確かめる）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceWriteConnectionUsageIT {

    /** DB の種類ごとの環境。 */
    private record Env(
            BusinessDataSampleSchema sample,
            BusinessDataTestApp app,
            Actor member,
            CountingDataSource internal,
            CountingDataSource reads,
            CountingDataSource writes) {}

    /**
     * 1要求の接続の数。
     *
     * @param status 状態コード
     * @param internal 内部DB を借りた回数
     * @param reads 対象DB の読み取りを借りた回数
     * @param writes 対象DB の書き込みを借りた回数
     * @param readInUseAtWrite 書き込みを借りた時点の読み取りの使用中の最大
     */
    private record Usage(int status, int internal, int reads, int writes, int readInUseAtWrite) {}

    /** 数える DB の種類。 */
    private static final List<DatabaseProduct> PRODUCTS =
            List.of(DatabaseProduct.POSTGRESQL, DatabaseProduct.MYSQL, DatabaseProduct.MARIADB);

    private final Map<DatabaseProduct, Env> envs = new EnumMap<>(DatabaseProduct.class);

    private final List<AutoCloseable> closeables = new ArrayList<>();

    private final AtomicInteger ids = new AtomicInteger(3_000);

    @BeforeAll
    void start(@TempDir Path dir) {
        for (DatabaseProduct product : PRODUCTS) {
            BusinessDataSampleSchema sample = new BusinessDataSampleSchema(product);
            sample.start();
            closeables.add(sample::stop);
            ReferenceTables tables = new ReferenceTables(sample);
            tables.createWrite();
            BusinessDataTestApp app = BusinessDataTestApp.start(
                    dir.resolve(product.name()), sample.target(), sample.writer(), new Class<?>[] {
                        CountingDataSourceConfig.class
                    });
            closeables.add(app);
            app.installDsl(sample.model(tables.writeTables()));
            Actor member = app.reader(tables.writeSettings(Map.of()));
            CountingDataSource internal = (CountingDataSource) app.bean("dataSource", DataSource.class);
            CountingDataSource reads = (CountingDataSource) app.bean("targetDataSource", DataSource.class);
            CountingDataSource writes = (CountingDataSource) app.bean("targetWriteDataSource", DataSource.class);
            reads.pairWith(writes);
            envs.put(product, new Env(sample, app, member, internal, reads, writes));
        }
    }

    @AfterAll
    void stop() throws Exception {
        for (int i = closeables.size() - 1; i >= 0; i--) {
            closeables.get(i).close();
        }
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private Map<String, Object> body(Env env, String table) {
        Map<String, Object> body = env.sample().body(table);
        body.put(
                "definitionId",
                env.app().definitionId(env.member().token(), env.sample().schema(), table));
        return body;
    }

    private Usage measure(Env env, String table, String method, Map<String, String> key, Map<String, String> values) {
        Map<String, Object> body = body(env, table);
        if (key != null) {
            body.put("key", key);
        }
        if (values != null) {
            body.put("values", values);
        }
        assertThat(env.internal().inUse()).as("要求の始まりに内部DB を借りていない").isZero();
        env.internal().reset();
        env.reads().reset();
        env.writes().reset();
        HttpResponse<String> response =
                switch (method) {
                    case "create" ->
                        env.app().post(BusinessDataPaths.ROWS, env.member().token(), body);
                    case "update" ->
                        env.app().patch(BusinessDataPaths.ROWS, env.member().token(), body);
                    default ->
                        env.app().post(BusinessDataPaths.DELETE, env.member().token(), body);
                };
        assertThat(env.internal().peak()).as("内部DB の同時の最大").isLessThanOrEqualTo(1);
        assertThat(env.internal().inUse()).as("応答の後に内部DB を返している").isZero();
        assertThat(env.reads().inUse()).isZero();
        assertThat(env.writes().inUse()).isZero();
        return new Usage(
                response.statusCode(),
                env.internal().borrows(),
                env.reads().borrows(),
                env.writes().borrows(),
                env.writes().peerInUseAtBorrow());
    }

    private String id() {
        return Integer.toString(ids.incrementAndGet());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = DatabaseProduct.class,
            names = {"POSTGRESQL", "MYSQL", "MARIADB"})
    @DisplayName("0, 1, 3 and 5 references borrow 0 or 1 read and 1 write, never both at once")
    void createsBorrowAtMostOneRead(DatabaseProduct product) {
        Env env = envs.get(product);

        assertThat(measure(env, WS_MAIN, "create", null, values("id", id(), "name", "参照なし")))
                .isEqualTo(new Usage(201, 3, 0, 1, 0));
        assertThat(measure(env, WS_MAIN, "create", null, values("id", id(), "ref", "V1")))
                .isEqualTo(new Usage(201, 3, 1, 1, 0));
        assertThat(measure(env, WS_FIVE, "create", null, values("id", id(), "r1", "V1", "r2", "V2", "r3", "V3")))
                .isEqualTo(new Usage(201, 3, 1, 1, 0));
        assertThat(measure(
                        env,
                        WS_FIVE,
                        "create",
                        null,
                        values("id", id(), "r1", "V1", "r2", "V2", "r3", "V3", "r4", "K1", "r5", "007")))
                .isEqualTo(new Usage(201, 3, 1, 1, 0));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = DatabaseProduct.class,
            names = {"POSTGRESQL", "MYSQL", "MARIADB"})
    @DisplayName("updates borrow one read for the previous row, rejections borrow no write and deletes no read")
    void updatesRejectionsAndDeletes(DatabaseProduct product) {
        Env env = envs.get(product);
        String base = id();
        env.sample().addObjects("INSERT INTO %s" + WS_MAIN + " (id, ref) VALUES (" + base + ", 'V1')");

        assertThat(measure(env, WS_MAIN, "update", Map.of("id", base), values("ref", "V1", "name", "x")))
                .as("値の変わらない更新")
                .isEqualTo(new Usage(200, 3, 1, 1, 0));
        assertThat(measure(env, WS_MAIN, "update", Map.of("id", base), values("ref", "V2")))
                .as("値の変わった更新")
                .isEqualTo(new Usage(200, 3, 1, 1, 0));
        assertThat(measure(env, WS_MAIN, "update", Map.of("id", base), values("name", "y")))
                .as("参照の無い更新は今と同じ")
                .isEqualTo(new Usage(200, 3, 0, 1, 0));
        assertThat(measure(env, WS_MAIN, "create", null, values("id", id(), "ref", "V9")))
                .as("拒否は書き込みを借りない")
                .isEqualTo(new Usage(409, 3, 1, 0, 0));
        assertThat(measure(env, WS_MAIN, "delete", Map.of("id", base), null))
                .as("削除は ⑩ を通らない")
                .isEqualTo(new Usage(200, 3, 0, 1, 0));
    }

    @Test
    @DisplayName("BR4.7: two required references to the same target count it with one statement (MySQL)")
    void sameTargetCountedOnce() {
        Env env = envs.get(DatabaseProduct.MYSQL);
        try (StatementLog log = new StatementLog(env.sample().database())) {
            String mark = log.mark();

            Usage usage = measure(env, WS_REQ_TWO, "create", null, values("id", id(), "a", "M00001", "b", "M00002"));

            assertThat(usage).isEqualTo(new Usage(409, 3, 1, 0, 0));
            assertThat(log.queriesSince(mark, env.sample().reader()))
                    .filteredOn(q -> q.contains(W_MANY))
                    .as("TOO_MANY のため check を流さず、countState の文は1つ")
                    .hasSize(1);
        }
    }
}
