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
import cherry.mastersmith.businessdata.testsupport.StallingRelay;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.SilentServer;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
 * 対象DB とうまくいかないときの時間と code（Step 19、BR1.8・BR5.6・BR8.2、NFR4.4・NFR4.8 の読み替え「25 秒以内に 503」、
 * reliability-design.md の 5節、D3: A）。
 *
 * <ul>
 *   <li>応答しない対象DB（{@link SilentServer}）・接続を拒む番号・設定が無い: 503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}
 *   <li>本物の遅い問い合わせ（3種類の DB の眠る関数を含むビュー）: 件数とページのそれぞれで約 5 秒の 503
 *       {@code BUSINESS_TARGET_DB_TIMEOUT}
 *   <li>つないだ後に止まる（{@link StallingRelay}、3種類すべて）: 25 秒以内に 503 {@code BUSINESS_TARGET_DB_TIMEOUT}、止めを解いた
 *       後の次の要求はすぐ成功する
 * </ul>
 *
 * <p>時間の確かめは、上限に対する上限の側だけを見る（「これより長くかからない」）。どれも応答に接続先・ユーザー名・例外の文が無い。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataTimeoutIT {

    /** 件数で眠るビュー。 */
    private static final String SLOW_COUNT = "slow_count";

    /** ページで眠るビュー。 */
    private static final String SLOW_PAGE = "slow_page";

    /** DB の種類ごとの環境。 */
    private record Env(
            BusinessDataSampleSchema sample,
            BusinessDataTestApp app,
            Actor member,
            StallingRelay relay,
            BusinessDataTestApp relayApp,
            Actor relayMember) {}

    private final Map<DatabaseProduct, Env> envs = new EnumMap<>(DatabaseProduct.class);

    private final List<AutoCloseable> closeables = new ArrayList<>();

    private Path dir;

    @BeforeAll
    void start(@TempDir Path tempDir) {
        dir = tempDir;
        for (DatabaseProduct product : DatabaseProduct.values()) {
            envs.put(product, env(product));
        }
    }

    @AfterAll
    void stop() throws Exception {
        for (int i = closeables.size() - 1; i >= 0; i--) {
            closeables.get(i).close();
        }
    }

    private Env env(DatabaseProduct product) {
        BusinessDataSampleSchema sample = new BusinessDataSampleSchema(product);
        sample.start();
        closeables.add(sample::stop);
        boolean postgres = product == DatabaseProduct.POSTGRESQL;
        String sleep = postgres ? "pg_sleep(6)::text = ''" : "SLEEP(6) = 0";
        String sleepColumn = postgres ? "pg_sleep(6)::text" : "SLEEP(6)";
        sample.addObjects(
                "CREATE VIEW %s" + SLOW_COUNT + " AS SELECT id FROM %sitem WHERE " + sleep,
                "CREATE VIEW %s" + SLOW_PAGE + " AS SELECT id, " + sleepColumn + " AS s FROM %sitem");
        String integer = postgres ? "int4" : "int";
        DslModel model = sample.model(
                table(
                        SLOW_COUNT,
                        true,
                        List.of("id"),
                        column("id").dbType(integer).list(true, 1).build()),
                table(
                        SLOW_PAGE,
                        true,
                        List.of("id"),
                        column("id").dbType(integer).list(true, 1).build(),
                        column("s").list(true, 2).build()));
        BusinessDataTestApp app = BusinessDataTestApp.start(dir.resolve(product.name() + "-main"), sample.target());
        closeables.add(app);
        app.installDsl(model);
        Target direct = sample.target();
        StallingRelay relay = StallingRelay.start(direct.host(), direct.port());
        closeables.add(relay);
        BusinessDataTestApp relayApp = BusinessDataTestApp.start(
                dir.resolve(product.name() + "-relay"),
                new Target(
                        product,
                        "127.0.0.1",
                        relay.port(),
                        direct.database(),
                        direct.schema(),
                        direct.user(),
                        direct.password()));
        closeables.add(relayApp);
        relayApp.installDsl(model);
        return new Env(
                sample,
                app,
                app.reader(sample.settings(MainPermission.READ)),
                relay,
                relayApp,
                relayApp.reader(sample.settings(MainPermission.READ)));
    }

    private record Timed(HttpResponse<String> response, Duration elapsed) {}

    private static Timed timed(Supplier<HttpResponse<String>> call) {
        long start = System.nanoTime();
        HttpResponse<String> response = call.get();
        return new Timed(response, Duration.ofNanos(System.nanoTime() - start));
    }

    private static HttpResponse<String> search(
            BusinessDataTestApp app, Actor member, BusinessDataSampleSchema sample, String table) {
        return app.post(BusinessDataPaths.SEARCH, member.token(), sample.body(table, "page", 1, "size", 20));
    }

    private static void assertProblem(Timed timed, int status, String code, Duration within, Target target) {
        assertThat(timed.response().statusCode()).as(timed.response().body()).isEqualTo(status);
        assertThat(HttpTestClient.json(timed.response())).containsEntry("code", code);
        assertThat(timed.elapsed()).as("上限の側だけを見る").isLessThan(within);
        assertThat(timed.response().body())
                .doesNotContain(target.user())
                .doesNotContain(target.password())
                .doesNotContain(":" + target.port())
                .doesNotContain("Exception")
                .doesNotContain("jdbc:");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName(
            "a slow count fails the whole request and a slow page fails too, each as 503 TIMEOUT in about 5 seconds")
    void slowQueries(DatabaseProduct product) {
        Env env = envs.get(product);

        Timed count = timed(() -> search(env.app(), env.member(), env.sample(), SLOW_COUNT));
        Timed page = timed(() -> search(env.app(), env.member(), env.sample(), SLOW_PAGE));

        assertProblem(
                count,
                503,
                "BUSINESS_TARGET_DB_TIMEOUT",
                Duration.ofSeconds(10),
                env.sample().target());
        assertProblem(
                page,
                503,
                "BUSINESS_TARGET_DB_TIMEOUT",
                Duration.ofSeconds(10),
                env.sample().target());
        assertThat(count.elapsed()).as("件数の上限 5 秒").isGreaterThanOrEqualTo(Duration.ofSeconds(4));
        Timed after = timed(() -> search(env.app(), env.member(), env.sample(), BusinessDataSampleSchema.ITEM));
        assertThat(after.response().statusCode()).as("時間切れの後も読める").isEqualTo(200);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("a target that stops after connecting is 503 TIMEOUT within 25 seconds and the next request succeeds")
    void stallAfterConnecting(DatabaseProduct product) {
        Env env = envs.get(product);
        assertThat(search(env.relayApp(), env.relayMember(), env.sample(), BusinessDataSampleSchema.ITEM)
                        .statusCode())
                .as("中継を通して読める（プールに接続ができる）")
                .isEqualTo(200);

        if (product == DatabaseProduct.POSTGRESQL) {
            env.relay().arm("COUNT(");
        } else {
            // MySQL・MariaDB は目印の文字で止めない（MySQL は既定で TLS）。件数の段の最初のまとまりで止める。U2 で時差の文を接続を
            // 作るときの初期化に移した（D5）ため、貸すたびの SET time_zone は無い。手元の確かめ（DB の general_log）で、1回の一覧は
            // MySQL 9.7（Connector/J）が「SET sql_select_limit=1・件数・SET sql_select_limit=20・ページ」、MariaDB（3.5）が「件数・
            // ページ」のまとまりになった。どちらも最初のまとまり（MySQL は件数の前の SET、MariaDB は件数）で止める（U1 の G23 の続き、
            // U2 の generation-notes.md の N20）。MySQL で件数の問い合わせそのものを止めると、Connector/J の文の上限で 503 TIMEOUT に
            // なった後に壊れた接続がプールに戻り、次の1回が 503 UNAVAILABLE になる（U2 の G15）。止めた段が件数の段であることは、503
            // TIMEOUT（貸し出しの始めで止めると UNAVAILABLE）で確かめる。
            env.relay().armAfterLargeChunks(0);
        }
        Timed stalled =
                timed(() -> search(env.relayApp(), env.relayMember(), env.sample(), BusinessDataSampleSchema.ITEM));
        int stalledConnections = env.relay().stalledCount();
        String report = env.relay().stallReport();
        env.relay().release();
        Timed next =
                timed(() -> search(env.relayApp(), env.relayMember(), env.sample(), BusinessDataSampleSchema.ITEM));

        assertThat(stalledConnections).as("中継が問い合わせで止めた " + report).isEqualTo(1);
        assertThat(stalled.response().statusCode())
                .as(report + " " + stalled.response().body())
                .isEqualTo(503);
        assertProblem(
                stalled,
                503,
                "BUSINESS_TARGET_DB_TIMEOUT",
                Duration.ofSeconds(25),
                env.sample().target());
        assertThat(next.response().statusCode()).as(next.response().body()).isEqualTo(200);
        assertThat(next.elapsed()).as("壊れた接続はプールが捨て、次はすぐ読める").isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("a silent target, a refused port and a missing setting are 503 UNAVAILABLE")
    void unavailableTargets() {
        Target base = envs.get(DatabaseProduct.POSTGRESQL).sample().target();
        BusinessDataSampleSchema sample = envs.get(DatabaseProduct.POSTGRESQL).sample();
        try (SilentServer silent = SilentServer.start()) {
            BusinessDataTestApp silentApp = BusinessDataTestApp.start(
                    dir.resolve("silent"),
                    new Target(
                            base.product(),
                            "127.0.0.1",
                            silent.port(),
                            base.database(),
                            base.schema(),
                            base.user(),
                            base.password()));
            closeables.add(silentApp);
            silentApp.installDsl(sample.model());
            Actor silentMember = silentApp.reader(sample.settings(MainPermission.READ));
            Timed borrow = timed(() -> search(silentApp, silentMember, sample, BusinessDataSampleSchema.ITEM));
            assertProblem(borrow, 503, "BUSINESS_TARGET_DB_UNAVAILABLE", Duration.ofSeconds(8), base);
        }
        BusinessDataTestApp refusedApp = BusinessDataTestApp.start(
                dir.resolve("refused"),
                new Target(
                        base.product(), base.host(), 1, base.database(), base.schema(), base.user(), base.password()));
        closeables.add(refusedApp);
        refusedApp.installDsl(sample.model());
        Actor refusedMember = refusedApp.reader(sample.settings(MainPermission.READ));
        assertProblem(
                timed(() -> search(refusedApp, refusedMember, sample, BusinessDataSampleSchema.ITEM)),
                503,
                "BUSINESS_TARGET_DB_UNAVAILABLE",
                Duration.ofSeconds(8),
                base);
        BusinessDataTestApp noTarget = BusinessDataTestApp.start(dir.resolve("none"), null);
        closeables.add(noTarget);
        noTarget.installDsl(sample.model());
        Actor noTargetMember = noTarget.reader(sample.settings(MainPermission.READ));
        assertProblem(
                timed(() -> search(noTarget, noTargetMember, sample, BusinessDataSampleSchema.ITEM)),
                503,
                "BUSINESS_TARGET_DB_UNAVAILABLE",
                Duration.ofSeconds(2),
                base);
        assertThat(noTarget.post(
                                BusinessDataPaths.DEFINITION,
                                noTargetMember.token(),
                                sample.body(BusinessDataSampleSchema.ITEM))
                        .statusCode())
                .as("定義は対象DB に接続しない")
                .isEqualTo(200);
    }
}
