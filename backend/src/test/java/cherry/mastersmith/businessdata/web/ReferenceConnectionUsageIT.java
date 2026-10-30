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
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp.Target;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.businessdata.testsupport.StatementLog;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.SilentServer;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
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

/**
 * 定義の取得で参照先を読むときの接続の数を、決定的に確かめる結合テスト（Intent 261029-reference-lookup-cleanup の U1、Step 12、
 * BR8.2・BR8.3・BR4.7、T-C2、(j) の定義の分。既存の {@code BusinessDataConnectionUsageIT} と同じ形）。PostgreSQL と MySQL の
 * コンテナ。文の数は MySQL の一般ログ（{@link StatementLog}）で数える。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
@SuppressWarnings("unchecked")
class ReferenceConnectionUsageIT {

    /** 参照先の表の数。 */
    private static final int TARGETS = 5;

    /** DB の種類ごとの環境。 */
    private record Env(BusinessDataSampleSchema sample, BusinessDataTestApp app, Actor member) {}

    private Env postgres;

    private Env mysql;

    private Path dir;

    private final List<AutoCloseable> closeables = new ArrayList<>();

    @BeforeAll
    void start(@TempDir Path tempDir) {
        dir = tempDir;
        postgres = env(DatabaseProduct.POSTGRESQL);
        mysql = env(DatabaseProduct.MYSQL);
    }

    @AfterAll
    void stop() throws Exception {
        for (int i = closeables.size() - 1; i >= 0; i--) {
            closeables.get(i).close();
        }
    }

    private static OptionSource reference(String table) {
        return new OptionSource(OptionSourceKind.REFERENCE, List.of(), table, "v", "l", List.of(), List.of());
    }

    private static DslColumn ref(String name, String table) {
        return column(name).form(FormPart.SELECT).options(reference(table)).build();
    }

    private static DslTable source(String name, String integer, DslColumn... references) {
        List<DslColumn> columns =
                new ArrayList<>(List.of(column("id").dbType(integer).build()));
        columns.addAll(List.of(references));
        return table(name, false, List.of("id"), columns.toArray(DslColumn[]::new));
    }

    /** 参照先 t1〜t5 と、参照の数の違う参照元の DSL。 */
    private static DslModel model(BusinessDataSampleSchema sample) {
        String integer = sample.database().product() == DatabaseProduct.POSTGRESQL ? "int4" : "int";
        List<DslTable> tables = new ArrayList<>();
        for (int i = 1; i <= TARGETS; i++) {
            tables.add(table(
                    "t" + i,
                    false,
                    List.of("id"),
                    column("id").dbType(integer).build(),
                    column("v").build(),
                    column("l").build()));
        }
        tables.add(source("src0", integer));
        tables.add(source("src1", integer, ref("a", "t1")));
        tables.add(source("src3", integer, ref("a", "t1"), ref("b", "t2"), ref("c", "t3")));
        tables.add(source(
                "src5", integer, ref("a", "t1"), ref("b", "t2"), ref("c", "t3"), ref("d", "t4"), ref("e", "t5")));
        tables.add(source("srcsame", integer, ref("a", "t1"), ref("b", "t1")));
        tables.add(source(
                "srclookup",
                integer,
                column("p")
                        .form(FormPart.LOOKUP)
                        .options(new OptionSource(
                                OptionSourceKind.LOOKUP,
                                List.of(),
                                "t1",
                                "v",
                                null,
                                List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)),
                                List.of(new LookupListItem("l", 1))))
                        .build()));
        return sample.model(tables.toArray(DslTable[]::new));
    }

    private static Map<PermissionTarget, PermissionValues> settings(BusinessDataSampleSchema sample) {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(sample.schema()), new PermissionValues(MainPermission.FULL, true, true));
        return settings;
    }

    private Env env(DatabaseProduct product) {
        BusinessDataSampleSchema sample = new BusinessDataSampleSchema(product);
        sample.start();
        closeables.add(sample::stop);
        List<String> sql = new ArrayList<>();
        for (int i = 1; i <= TARGETS; i++) {
            sql.add("CREATE TABLE %st" + i
                    + " (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(20) NULL, l VARCHAR(20) NULL)");
            sql.add("INSERT INTO %st" + i + " (id, v, l) VALUES (1, 'v1', 'l1'), (2, 'v2', 'l2')");
        }
        for (String name : List.of("src0", "src1", "src3", "src5", "srcsame", "srclookup")) {
            sql.add("CREATE TABLE %s" + name + " (id INTEGER NOT NULL PRIMARY KEY)");
        }
        sample.addObjects(sql.toArray(String[]::new));
        BusinessDataTestApp app = BusinessDataTestApp.start(
                dir.resolve(product.name()), sample.target(), new Class<?>[] {CountingDataSourceConfig.class});
        closeables.add(app);
        app.installDsl(model(sample));
        return new Env(sample, app, app.reader(settings(sample)));
    }

    private static CountingDataSource counter(BusinessDataTestApp app, String name) {
        return (CountingDataSource) app.bean(name, DataSource.class);
    }

    private static HttpResponse<String> definition(Env env, String table, String purpose) {
        Map<String, Object> body = env.sample().body(table);
        if (purpose != null) {
            body.put("purpose", purpose);
        }
        return env.app().post(BusinessDataPaths.DEFINITION, env.member().token(), body);
    }

    static Stream<Arguments> cases() {
        // 参照元, 用途, 対象DB を借りた回数, 参照先の文の数（MySQL の一般ログ）
        return Stream.of(
                Arguments.of("src0", "CREATE", 0, 0),
                Arguments.of("src1", "CREATE", 1, 1),
                Arguments.of("src3", "UPDATE", 1, 3),
                Arguments.of("src5", "CREATE", 1, 5),
                Arguments.of("srcsame", "CREATE", 1, 1),
                Arguments.of("srclookup", "CREATE", 0, 0),
                Arguments.of("src5", "VIEW", 0, 0),
                Arguments.of("src5", null, 0, 0));
    }

    @ParameterizedTest(name = "{0} {1} on {4}: borrows {2}, statements {3}")
    @MethodSource("tableOnBoth")
    @DisplayName("the definition borrows the target at most once, after giving back the internal database")
    void connectionsPerDefinition(
            String source, String purpose, int targetBorrows, int statements, DatabaseProduct product) {
        Env env = product == DatabaseProduct.POSTGRESQL ? postgres : mysql;
        CountingDataSource internal = counter(env.app(), "dataSource");
        CountingDataSource target = counter(env.app(), "targetDataSource");
        internal.pairWith(target);
        definition(env, source, purpose);
        internal.reset();
        target.reset();

        HttpResponse<String> response;
        List<String> reached = null;
        if (product == DatabaseProduct.MYSQL) {
            try (StatementLog log = new StatementLog(env.sample().database())) {
                String mark = log.mark();
                response = definition(env, source, purpose);
                reached = log.queriesSince(mark, env.sample().reader());
            }
        } else {
            response = definition(env, source, purpose);
        }

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(internal.peak()).as("内部DB の同時の最大").isEqualTo(1);
        assertThat(target.borrows()).as("対象DB の貸し出し").isEqualTo(targetBorrows);
        assertThat(target.peak()).isEqualTo(targetBorrows);
        assertThat(target.peerInUseAtBorrow()).as("対象DB を借りるときに内部DB を借りていない").isZero();
        assertThat(internal.peerInUseAtBorrow()).as("対象DB を借りている間に内部DB を借りない").isZero();
        assertThat(internal.inUse()).isZero();
        assertThat(target.inUse()).isZero();
        if (reached != null) {
            assertThat(reached.stream().filter(q -> q.startsWith("SELECT")).toList())
                    .as("参照先の文の数 " + reached)
                    .hasSize(statements);
        }
    }

    static Stream<Arguments> tableOnBoth() {
        return cases().flatMap(arguments -> Stream.of(DatabaseProduct.POSTGRESQL, DatabaseProduct.MYSQL)
                .map(product -> {
                    Object[] values = arguments.get();
                    return Arguments.of(values[0], values[1], values[2], values[3], product);
                }));
    }

    @Test
    @DisplayName("the list and the detail keep their statement counts (T-C2)")
    void listAndDetailStatementsUnchanged() {
        try (StatementLog log = new StatementLog(mysql.sample().database())) {
            String mark = log.mark();
            HttpResponse<String> search = mysql.app()
                    .post(
                            BusinessDataPaths.SEARCH,
                            mysql.member().token(),
                            mysql.sample().body(BusinessDataSampleSchema.ITEM, "page", 1, "size", 20));
            List<String> searchQueries = log.queriesSince(mark, mysql.sample().reader());
            String mark2 = log.mark();
            HttpResponse<String> get = mysql.app()
                    .post(
                            BusinessDataPaths.GET,
                            mysql.member().token(),
                            mysql.sample().body(BusinessDataSampleSchema.ITEM, "key", Map.of("id", "1")));
            List<String> getQueries = log.queriesSince(mark2, mysql.sample().reader());

            assertThat(search.statusCode()).as(search.body()).isEqualTo(200);
            assertThat(get.statusCode()).as(get.body()).isEqualTo(200);
            assertThat(searchQueries.stream().filter(q -> q.startsWith("SELECT")))
                    .as("件数とページ")
                    .hasSize(2);
            assertThat(getQueries.stream().filter(q -> q.startsWith("SELECT")))
                    .as("詳細")
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("while waiting for a silent target the internal database is not held and the answer is 503")
    void silentTargetHoldsNoInternalConnection() throws Exception {
        Target base = postgres.sample().target();
        try (SilentServer silent = SilentServer.start()) {
            BusinessDataTestApp app = BusinessDataTestApp.start(
                    dir.resolve("silent"),
                    new Target(
                            base.product(),
                            "127.0.0.1",
                            silent.port(),
                            base.database(),
                            base.schema(),
                            base.user(),
                            base.password()),
                    new Class<?>[] {CountingDataSourceConfig.class});
            closeables.add(app);
            app.installDsl(model(postgres.sample()));
            Actor member = app.reader(settings(postgres.sample()));
            CountingDataSource internal = counter(app, "dataSource");
            CountingDataSource target = counter(app, "targetDataSource");
            internal.pairWith(target);
            internal.reset();
            target.reset();

            HttpResponse<String> response = app.post(
                    BusinessDataPaths.DEFINITION,
                    member.token(),
                    postgres.sample().body("src3", "purpose", "CREATE"));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(503);
            assertThat(HttpTestClient.json(response)).containsEntry("code", "BUSINESS_TARGET_DB_UNAVAILABLE");
            assertThat(target.peerInUseAtBorrow()).as("対象DB を待つ間に内部DB を借りていない").isZero();
            assertThat(internal.inUse()).isZero();
        }
    }
}
