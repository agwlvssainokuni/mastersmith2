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
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.tableWithForeignKeys;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp.Target;
import cherry.mastersmith.businessdata.testsupport.StallingRelay;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.SilentServer;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 書き込みで対象DB とうまくいかないときの時間と code（Intent 261016-business-data-views の U2、計画の Step 21、BR6.4・BR10.1、D3・D4・
 * D7: A、NFR 設計のレビューの R-05・R-07、reliability-design.md の 5節）。
 *
 * <ul>
 *   <li>応答しない対象DB（{@link SilentServer}）・接続を拒む番号: 借りる段の 503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}
 *   <li>つないだ後に止まる（{@link StallingRelay}、3種類すべて）
 *       <ul>
 *         <li>書き込みの文の段: 503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}（保存されない。D3）
 *         <li>確定の段: 503 {@code BUSINESS_TARGET_DB_TIMEOUT}（保存されたか分からない。監査は {@code UNKNOWN}）
 *         <li>違反の後のセーブポイントへの戻しの段: 503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}（保存されない。D4）
 *       </ul>
 *       どれも 25 秒以内に返り、止めを解いた後の次の要求は通る
 * </ul>
 *
 * <p>止める位置: PostgreSQL と MariaDB は平文の通信のため、文の文字（{@code INSERT INTO}・{@code ROLLBACK TO SAVEPOINT}、MariaDB の
 * 確定は問い合わせの種類の 1 バイトを付けた {@code COMMIT}）で止める。PostgreSQL のドライバーは確定の文を名前つきで準備して文字を送らない
 * ため、確定は INSERT の次のまとまりで止める（{@link StallingRelay#armAfter(String)}）。MySQL は既定で TLS のため、手元の確かめ（DB の general_log と中継のまとまりの大きさ）で、1回の作成が「分離の水準の読み・READ
 * COMMITTED・autocommit=0・SAVEPOINT・INSERT・COMMIT・autocommit=1・REPEATABLE READ」の8つの 40 バイト以上のまとまりになると分かった
 * （Connector/J 9.7）。書き込みの文は4つ、確定と違反の後の戻しは5つを通した次で止める（U2 の generation-notes.md）。止めた位置は
 * {@link StallingRelay#stallReport()} を表明の説明に残す（U1 のレビューの R-05）。どの応答とログにも、接続先・ユーザー名・パスワード・
 * 例外の文・入れた値が無い。時間は上限の側だけを見る（実測の時間は generation-notes.md に DB ごとに記録する。NFR 設計のレビューの R-07）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込みを先、コンテナの実行環境の確かめを後にする（ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
class BusinessDataWriteTimeoutIT {

    /** 外部キーで {@code item} を指す表（違反の後の確かめの段で止めるため）。 */
    private static final String CHILD = "child";

    /** 入れる値（漏えいの確かめに使う架空の値）。 */
    private static final String VALUE = "書き込み架空-5309";

    /** 止める段。 */
    enum Stage {
        WRITE,
        COMMIT,
        INSPECT
    }

    /** DB の種類ごとの環境。 */
    private record Env(
            BusinessDataSampleSchema sample,
            StallingRelay relay,
            BusinessDataTestApp app,
            Actor member,
            Map<String, Object> definitionId) {}

    private final Map<DatabaseProduct, Env> envs = new EnumMap<>(DatabaseProduct.class);

    private final List<AutoCloseable> closeables = new ArrayList<>();

    private final AtomicInteger nextId = new AtomicInteger(1000);

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

    private static DslModel model(BusinessDataSampleSchema sample, DatabaseProduct product) {
        String integer = product == DatabaseProduct.POSTGRESQL ? "int4" : "int";
        return sample.model(tableWithForeignKeys(
                CHILD,
                List.of("id"),
                List.of(new DslForeignKey(List.of("item_id"), ITEM, List.of("id"))),
                column("id").dbType(integer).build(),
                column("item_id").dbType(integer).build()));
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
        sample.addObjects("CREATE TABLE %s" + CHILD
                + " (id INTEGER NOT NULL PRIMARY KEY, item_id INTEGER NULL REFERENCES %s" + ITEM + " (id))");
        Target direct = sample.target();
        StallingRelay relay = StallingRelay.start(direct.host(), direct.port());
        closeables.add(relay);
        BusinessDataTestApp app = BusinessDataTestApp.start(
                dir.resolve(product.name() + "-relay"),
                new Target(
                        product,
                        "127.0.0.1",
                        relay.port(),
                        direct.database(),
                        direct.schema(),
                        direct.user(),
                        direct.password()),
                sample.writer(),
                new Class<?>[0]);
        closeables.add(app);
        app.installDsl(model(sample, product));
        Actor member = app.reader(settings(sample));
        return new Env(sample, relay, app, member, app.definitionId(member.token(), sample.schema(), ITEM));
    }

    private record Timed(HttpResponse<String> response, Duration elapsed) {}

    private static Timed timed(Supplier<HttpResponse<String>> call) {
        long start = System.nanoTime();
        HttpResponse<String> response = call.get();
        return new Timed(response, Duration.ofNanos(System.nanoTime() - start));
    }

    private HttpResponse<String> create(Env env, String table, Map<String, String> values) {
        Map<String, Object> body = env.sample().body(table);
        body.put("definitionId", env.definitionId());
        body.put("values", values);
        return env.app().post(BusinessDataPaths.ROWS, env.member().token(), body);
    }

    private HttpResponse<String> createItem(Env env, String id) {
        return create(env, ITEM, Map.of("id", id, "name", VALUE));
    }

    private static void assertProblem(Timed timed, int status, String code, Duration within, Target target) {
        assertThat(timed.response().statusCode()).as(timed.response().body()).isEqualTo(status);
        assertThat(HttpTestClient.json(timed.response())).containsEntry("code", code);
        assertThat(timed.elapsed()).as("上限の側だけを見る").isLessThan(within);
        assertNoSecrets(timed.response().body(), target);
    }

    private static void assertNoSecrets(String text, Target target) {
        assertThat(text)
                .doesNotContain(target.user())
                .doesNotContain(target.password())
                .doesNotContain(":" + target.port())
                .doesNotContain("jdbc:mysql")
                .doesNotContain("jdbc:mariadb")
                .doesNotContain("jdbc:postgresql")
                .doesNotContain(VALUE);
    }

    /** 段ごとに止める位置を置く（PostgreSQL・MariaDB は文の文字、MySQL は通すまとまりの数）。 */
    private static void arm(Env env, DatabaseProduct product, Stage stage) {
        if (product == DatabaseProduct.MYSQL) {
            env.relay()
                    .armAfterLargeChunks(
                            switch (stage) {
                                case WRITE -> 4;
                                case COMMIT, INSPECT -> 5;
                            });
            return;
        }
        switch (stage) {
            case WRITE -> env.relay().arm("INSERT INTO");
            // PostgreSQL のドライバーは COMMIT を名前つきで準備して文の文字を送らないため、INSERT の次のまとまりで止める。MariaDB は
            // READ COMMITTED の文字に当たらないよう、問い合わせの種類の 1 バイト（0x03）を付けた COMMIT で止める。
            case COMMIT -> {
                if (product == DatabaseProduct.POSTGRESQL) {
                    env.relay().armAfter("INSERT INTO");
                } else {
                    env.relay().arm("\u0003COMMIT");
                }
            }
            case INSPECT -> env.relay().arm("ROLLBACK TO SAVEPOINT");
        }
    }

    private Map<String, Object> lastAudit(Env env) {
        return env.app()
                .bean(JdbcTemplate.class)
                .queryForMap("SELECT event_type, result, failure_reason FROM audit_events"
                        + " WHERE event_type LIKE 'BUSINESS_%' ORDER BY audit_event_id DESC LIMIT 1");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("stalls in the write, commit and post-violation stages are 503 within 25 seconds and then recover")
    void stallAfterConnecting(DatabaseProduct product, CapturedOutput output) {
        Env env = envs.get(product);
        Target target = env.sample().target();
        Map<Stage, Duration> elapsed = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            String warmId = Integer.toString(nextId.getAndIncrement());
            assertThat(createItem(env, warmId).statusCode())
                    .as("中継を通して書ける（プールに接続ができる）")
                    .isEqualTo(201);
            String id = Integer.toString(nextId.getAndIncrement());
            arm(env, product, stage);

            Timed stalled = timed(() -> stage == Stage.INSPECT
                    ? create(env, CHILD, Map.of("id", id, "item_id", "999999"))
                    : createItem(env, id));
            String report = env.relay().stallReport();
            int stalledConnections = env.relay().stalledCount();
            // 次の要求の監査を数えないよう、止めた要求の監査を先に読む。
            Map<String, Object> audit = lastAudit(env);
            env.relay().release();
            Timed next = timed(() -> createItem(env, Integer.toString(nextId.getAndIncrement())));

            assertThat(stalledConnections).as(stage + " " + report).isEqualTo(1);
            switch (stage) {
                case WRITE, INSPECT ->
                    assertProblem(stalled, 503, "BUSINESS_TARGET_DB_UNAVAILABLE", Duration.ofSeconds(25), target);
                case COMMIT -> {
                    assertProblem(stalled, 503, "BUSINESS_TARGET_DB_TIMEOUT", Duration.ofSeconds(25), target);
                    assertThat(audit)
                            .as("確定の段は保存されたか分からない")
                            .containsEntry("EVENT_TYPE", "BUSINESS_ROW_CREATED")
                            .containsEntry("RESULT", "UNKNOWN")
                            .containsEntry("FAILURE_REASON", "BUSINESS_TARGET_DB_TIMEOUT");
                }
            }
            if (stage != Stage.COMMIT) {
                assertThat(env.sample().count(stage == Stage.INSPECT ? CHILD : ITEM, "id = " + id))
                        .as("保存されていない（" + stage + "）")
                        .isZero();
            }
            assertThat(next.response().statusCode())
                    .as(stage + " の後: " + next.response().body())
                    .isEqualTo(201);
            assertThat(next.elapsed()).as("壊れた接続はプールが捨て、次はすぐ書ける").isLessThan(Duration.ofSeconds(5));
            elapsed.put(stage, stalled.elapsed());
        }
        // 実測の時間（generation-notes.md に DB ごとに写す）。
        System.out.println("BusinessDataWriteTimeoutIT " + product + " elapsed=" + elapsed);
        // アプリのログだけを見る（Testcontainers の準備のログには、テストの DB の接続先が出る）。
        String appLogs = JsonLogRecords.parse(output.getAll()).stream()
                .filter(r -> !String.valueOf(r.get("logger")).startsWith("tc.")
                        && !String.valueOf(r.get("logger")).startsWith("org.testcontainers"))
                .toList()
                .toString();
        assertNoSecrets(appLogs, target);
        assertThat(appLogs)
                .doesNotContain(":" + env.relay().port())
                .doesNotContain("jdbc:" + product.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    @DisplayName("a silent target and a refused port are 503 UNAVAILABLE at the borrow stage without writing")
    void unavailableTargets() {
        Env env = envs.get(DatabaseProduct.POSTGRESQL);
        Target base = env.sample().target();
        DslModel model = model(env.sample(), DatabaseProduct.POSTGRESQL);
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
                            base.password()),
                    env.sample().writer(),
                    new Class<?>[0]);
            closeables.add(silentApp);
            silentApp.installDsl(model);
            Actor member = silentApp.reader(settings(env.sample()));
            Env silentEnv = new Env(
                    env.sample(),
                    env.relay(),
                    silentApp,
                    member,
                    silentApp.definitionId(member.token(), base.schema(), ITEM));
            assertProblem(
                    timed(() -> createItem(silentEnv, "8001")),
                    503,
                    "BUSINESS_TARGET_DB_UNAVAILABLE",
                    Duration.ofSeconds(8),
                    base);
        }
        BusinessDataTestApp refusedApp = BusinessDataTestApp.start(
                dir.resolve("refused"),
                new Target(
                        base.product(), base.host(), 1, base.database(), base.schema(), base.user(), base.password()),
                env.sample().writer(),
                new Class<?>[0]);
        closeables.add(refusedApp);
        refusedApp.installDsl(model);
        Actor member = refusedApp.reader(settings(env.sample()));
        Env refused = new Env(
                env.sample(),
                env.relay(),
                refusedApp,
                member,
                refusedApp.definitionId(member.token(), base.schema(), ITEM));
        assertProblem(
                timed(() -> createItem(refused, "8002")),
                503,
                "BUSINESS_TARGET_DB_UNAVAILABLE",
                Duration.ofSeconds(8),
                base);
        assertThat(env.sample().count(ITEM, "id IN (8001, 8002)")).isZero();
    }
}
