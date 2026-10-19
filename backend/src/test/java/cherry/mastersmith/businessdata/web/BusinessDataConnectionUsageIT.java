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
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 業務データの API が1要求で使う接続の数を、決定的に確かめる結合テスト（U1 の Step 18 と U2 の Step 24（NFR2.6）、BR8.3・BR8.4、NFR2.3、AC3.6.6・AC3.6.7、既存の
 * {@code NavigationConnectionUsageIT} と同じ形、NFR 設計のレビュー R-03）。PostgreSQL と MySQL のコンテナ（MySQL は貸し出しの始めの
 * {@code SET time_zone} の文が増えても接続が増えないことを確かめる行）。
 *
 * <p>U2 で書き込みの行（作成・違反の後の確かめを通る作成・更新・削除）を足した。書き込みは、書き込みの接続を1回だけ借り、借りている間に
 * 内部DB を借りず、読み取りの接続を借りない（scalability-design.md の 8節）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataConnectionUsageIT {

    /** 測る API。 */
    enum Call {
        DEFINITION,
        SEARCH,
        GET,
        /** 作成（U2）。 */
        CREATE,
        /** 主キーの重なりで違反し、違反の後の確かめを通る作成（U2）。 */
        CREATE_VIOLATION,
        /** 更新（U2）。 */
        UPDATE,
        /** 削除（U2）。 */
        DELETE
    }

    /** 作成・削除の行の主キー（呼ぶたびに新しい値）。 */
    private final AtomicInteger nextId = new AtomicInteger(1000);

    /** DB の種類ごとの環境。 */
    private record Env(BusinessDataSampleSchema sample, BusinessDataTestApp app, Actor member) {}

    private Env postgres;

    private Env mysql;

    @BeforeAll
    void start(@TempDir Path dir) {
        postgres = env(DatabaseProduct.POSTGRESQL, dir.resolve("pg"));
        mysql = env(DatabaseProduct.MYSQL, dir.resolve("mysql"));
    }

    private static Env env(DatabaseProduct product, Path dir) {
        BusinessDataSampleSchema sample = new BusinessDataSampleSchema(product);
        sample.start();
        BusinessDataTestApp app = BusinessDataTestApp.start(
                dir, sample.target(), sample.writer(), new Class<?>[] {CountingDataSourceConfig.class});
        app.installDsl(sample.model());
        return new Env(sample, app, app.reader(sample.writeSettings()));
    }

    @AfterAll
    void stop() {
        for (Env env : new Env[] {postgres, mysql}) {
            if (env == null) {
                continue;
            }
            try {
                env.app().close();
            } finally {
                env.sample().stop();
            }
        }
    }

    private static CountingDataSource counter(BusinessDataTestApp app, String name) {
        return (CountingDataSource) app.bean(name, DataSource.class);
    }

    static Stream<Arguments> table() {
        // 呼ぶ API, DB, 内部DB の同時の最大, 対象DB を借りた回数, 対象DB の同時の最大
        return Stream.of(
                Arguments.of(Call.DEFINITION, DatabaseProduct.POSTGRESQL, 1, 0, 0),
                Arguments.of(Call.SEARCH, DatabaseProduct.POSTGRESQL, 1, 1, 1),
                Arguments.of(Call.GET, DatabaseProduct.POSTGRESQL, 1, 1, 1),
                Arguments.of(Call.SEARCH, DatabaseProduct.MYSQL, 1, 1, 1),
                Arguments.of(Call.GET, DatabaseProduct.MYSQL, 1, 1, 1),
                // U2 の書き込み（対象DB は書き込みの接続。読み取りの接続は借りない）。
                Arguments.of(Call.CREATE, DatabaseProduct.POSTGRESQL, 1, 1, 1),
                Arguments.of(Call.CREATE_VIOLATION, DatabaseProduct.POSTGRESQL, 1, 1, 1),
                Arguments.of(Call.UPDATE, DatabaseProduct.POSTGRESQL, 1, 1, 1),
                Arguments.of(Call.DELETE, DatabaseProduct.POSTGRESQL, 1, 1, 1),
                Arguments.of(Call.CREATE, DatabaseProduct.MYSQL, 1, 1, 1),
                Arguments.of(Call.CREATE_VIOLATION, DatabaseProduct.MYSQL, 1, 1, 1));
    }

    private static boolean writes(Call call) {
        return call == Call.CREATE || call == Call.CREATE_VIOLATION || call == Call.UPDATE || call == Call.DELETE;
    }

    private Map<String, Object> writeBody(Env env) {
        Map<String, Object> body = env.sample().body("item");
        body.put(
                "definitionId",
                env.app().definitionId(env.member().token(), env.sample().schema(), "item"));
        return body;
    }

    private HttpResponse<String> write(Env env, Call call, Map<String, Object> prepared) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(prepared);
        return switch (call) {
            case CREATE -> {
                body.put("values", Map.of("id", Integer.toString(nextId.getAndIncrement()), "name", "接続"));
                yield env.app().post(BusinessDataPaths.ROWS, env.member().token(), body);
            }
            case CREATE_VIOLATION -> {
                body.put("values", Map.of("id", "1", "name", "重なり"));
                yield env.app().post(BusinessDataPaths.ROWS, env.member().token(), body);
            }
            case UPDATE -> {
                body.put("key", Map.of("id", "2"));
                body.put("values", Map.of("name", "接続" + nextId.getAndIncrement()));
                yield env.app().patch(BusinessDataPaths.ROWS, env.member().token(), body);
            }
            case DELETE -> {
                String id = Integer.toString(nextId.getAndIncrement());
                env.sample()
                        .database()
                        .execute("INSERT INTO "
                                + env.sample().database().q(env.sample().schema()) + ".item (id, name) VALUES (" + id
                                + ", '消す')");
                body.put("key", Map.of("id", id));
                yield env.app().post(BusinessDataPaths.DELETE, env.member().token(), body);
            }
            default -> throw new IllegalArgumentException(call.name());
        };
    }

    @ParameterizedTest(name = "{0} on {1}: internal {2}, target borrows {3}")
    @MethodSource("table")
    void connectionsPerRequest(
            Call call, DatabaseProduct product, int internalPeak, int targetBorrows, int targetPeak) {
        Env env = product == DatabaseProduct.POSTGRESQL ? postgres : mysql;
        CountingDataSource internal = counter(env.app(), "dataSource");
        CountingDataSource read = counter(env.app(), "targetDataSource");
        CountingDataSource writeTarget = counter(env.app(), "targetWriteDataSource");
        // 読み取りは読み取りの接続、書き込みは書き込みの接続を、内部DB と組にして数える（U2 の NFR2.6）。
        CountingDataSource target = writes(call) ? writeTarget : read;
        CountingDataSource other = writes(call) ? read : writeTarget;
        internal.pairWith(target);
        Function<Call, HttpResponse<String>> send = c -> switch (c) {
            case DEFINITION ->
                env.app()
                        .post(
                                BusinessDataPaths.DEFINITION,
                                env.member().token(),
                                env.sample().body("item"));
            case SEARCH ->
                env.app()
                        .post(
                                BusinessDataPaths.SEARCH,
                                env.member().token(),
                                env.sample()
                                        .body(
                                                "item",
                                                "conditions",
                                                List.of(Map.of("column", "name", "value", "ノ")),
                                                "page",
                                                1,
                                                "size",
                                                20));
            case GET ->
                env.app()
                        .post(
                                BusinessDataPaths.GET,
                                env.member().token(),
                                env.sample().body("item", "key", Map.of("id", "2")));
            case CREATE, CREATE_VIOLATION, UPDATE, DELETE -> write(env, c, writeBody(env));
        };
        send.apply(call);
        assertThat(internal.inUse()).as("要求の始まりに内部DB を借りていない").isZero();
        assertThat(target.inUse()).isZero();
        // 書き込みの本文の定義の識別は、数える前に読んでおく（定義の API の内部DB の貸し出しを数えない）。
        Map<String, Object> warmDefinition = writes(call) ? writeBody(env) : Map.of();
        internal.reset();
        target.reset();
        other.reset();

        HttpResponse<String> response = writes(call) ? write(env, call, warmDefinition) : send.apply(call);

        int expectedStatus =
                switch (call) {
                    case CREATE -> 201;
                    case CREATE_VIOLATION -> 409;
                    default -> 200;
                };
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        assertThat(other.borrows()).as("もう一方の対象DB の接続は借りない").isZero();
        assertThat(internal.peak()).as("内部DB の同時の最大").isEqualTo(internalPeak);
        assertThat(target.borrows()).as("対象DB の貸し出し（件数とページで1回）").isEqualTo(targetBorrows);
        assertThat(target.peak()).as("対象DB の同時の最大").isEqualTo(targetPeak);
        assertThat(target.peerInUseAtBorrow()).as("対象DB を借りるときに内部DB を借りていない").isZero();
        assertThat(internal.peerInUseAtBorrow()).as("対象DB を借りている間に内部DB を借りない").isZero();
        assertThat(internal.inUse()).isZero();
        assertThat(target.inUse()).isZero();
    }
}
