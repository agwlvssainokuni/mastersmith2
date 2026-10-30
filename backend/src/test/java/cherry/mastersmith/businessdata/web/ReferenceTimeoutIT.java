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
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
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

/**
 * 定義の取得で参照先を読むときの時間切れ・失敗の code（Intent 261029-reference-lookup-cleanup の U1、Step 12、BR1.5・BR8.4・BR8.7・
 * BR10.2、reliability-design.md 2節）。
 *
 * <ul>
 *   <li>本物の遅い問い合わせ（3種類の DB の眠る関数を含むビュー）: 文の上限で 503 {@code BUSINESS_TARGET_DB_TIMEOUT}。全体の上限を 2 秒に
 *       した起動では、文の上限が残りの時間に絞られ、約 2 秒で切れる
 *   <li>応答しない対象DB（{@link SilentServer}）: 接続を借りる待ちの上限で 503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}（既存の決まり）
 *   <li>DSL と対象DB の食い違い（対象DB に無いラベルのカラム）: 409 {@code BUSINESS_TARGET_DB_MISMATCH}
 * </ul>
 *
 * <p>どれも応答とログに接続先・ユーザー名・例外の文が無い。時間は上限の側だけを見る。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
class ReferenceTimeoutIT {

    /** 眠るビュー（参照先）。 */
    private static final String SLOW = "slow_ref";

    /** DB の種類ごとの環境。 */
    private record Env(
            BusinessDataSampleSchema sample,
            BusinessDataTestApp app,
            Actor member,
            BusinessDataTestApp shortApp,
            Actor shortMember) {}

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

    private static DslColumn ref(String name, String table, String value, String label) {
        return column(name)
                .form(FormPart.SELECT)
                .options(new OptionSource(
                        OptionSourceKind.REFERENCE, List.of(), table, value, label, List.of(), List.of()))
                .build();
    }

    private static DslModel model(BusinessDataSampleSchema sample) {
        String integer = sample.database().product() == DatabaseProduct.POSTGRESQL ? "int4" : "int";
        return sample.model(
                table(
                        SLOW,
                        true,
                        List.of("id"),
                        column("id").dbType(integer).build(),
                        column("v").build(),
                        column("l").build()),
                table(
                        "src_slow",
                        false,
                        List.of("id"),
                        column("id").dbType(integer).build(),
                        ref("a", SLOW, "v", "l")),
                table(
                        "src_mismatch",
                        false,
                        List.of("id"),
                        column("id").dbType(integer).build(),
                        // other に ghost のカラムは無い（DSL と対象DB の食い違い）。
                        ref("a", BusinessDataSampleSchema.OTHER, "title", "ghost")),
                table(
                        BusinessDataSampleSchema.OTHER,
                        false,
                        List.of("id"),
                        column("id").dbType(integer).build(),
                        column("title").build(),
                        column("ghost").build()));
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
        boolean postgres = product == DatabaseProduct.POSTGRESQL;
        String sleepColumn = postgres ? "pg_sleep(8)::text" : "CAST(SLEEP(8) AS CHAR)";
        sample.addObjects(
                "CREATE VIEW %s" + SLOW + " AS SELECT id, " + sleepColumn + " AS v, title AS l FROM %sother",
                "CREATE TABLE %ssrc_slow (id INTEGER NOT NULL PRIMARY KEY)",
                "CREATE TABLE %ssrc_mismatch (id INTEGER NOT NULL PRIMARY KEY)");
        BusinessDataTestApp app = BusinessDataTestApp.start(dir.resolve(product.name() + "-main"), sample.target());
        closeables.add(app);
        app.installDsl(model(sample));
        BusinessDataTestApp shortApp = BusinessDataTestApp.start(
                dir.resolve(product.name() + "-short"),
                sample.target(),
                "--mastersmith.target-db.reference-read-total=2s");
        closeables.add(shortApp);
        shortApp.installDsl(model(sample));
        return new Env(sample, app, app.reader(settings(sample)), shortApp, shortApp.reader(settings(sample)));
    }

    private record Timed(HttpResponse<String> response, Duration elapsed) {}

    private static Timed definition(
            BusinessDataTestApp app, Actor member, BusinessDataSampleSchema sample, String table) {
        long start = System.nanoTime();
        HttpResponse<String> response =
                app.post(BusinessDataPaths.DEFINITION, member.token(), sample.body(table, "purpose", "CREATE"));
        return new Timed(response, Duration.ofNanos(System.nanoTime() - start));
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

    private static void assertNoDriverMessages(CapturedOutput output, Target target) {
        assertThat(output.getAll())
                .doesNotContain("canceling statement")
                .doesNotContain("Query execution was interrupted")
                .doesNotContain("Unknown column")
                // 対象DB に無いカラムの名前は、ドライバーの例外の文にだけ入る（Flyway の「does not exist yet」は別の行）。
                .doesNotContain("ghost")
                .doesNotContain(target.password());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("a slow target is 503 TIMEOUT by the 5 second statement limit and the next definition still works")
    void slowTarget(DatabaseProduct product, CapturedOutput output) {
        Env env = envs.get(product);

        Timed slow = definition(env.app(), env.member(), env.sample(), "src_slow");

        assertProblem(
                slow,
                503,
                "BUSINESS_TARGET_DB_TIMEOUT",
                Duration.ofSeconds(10),
                env.sample().target());
        assertThat(slow.elapsed()).as("文の上限 5 秒").isGreaterThanOrEqualTo(Duration.ofSeconds(4));
        assertThat(output.getAll()).contains("対象DB の参照先を読めませんでした");
        assertNoDriverMessages(output, env.sample().target());
        Timed after = definition(env.app(), env.member(), env.sample(), "src_mismatch");
        assertThat(after.response().statusCode()).as("時間切れの後も読める（409 は読んだ結果）").isEqualTo(409);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName(
            "with a total limit of 2 seconds the statement limit is narrowed and the definition fails in about 2 s")
    void narrowedByTheTotal(DatabaseProduct product) {
        Env env = envs.get(product);

        Timed slow = definition(env.shortApp(), env.shortMember(), env.sample(), "src_slow");

        assertProblem(
                slow,
                503,
                "BUSINESS_TARGET_DB_TIMEOUT",
                Duration.ofMillis(4_500),
                env.sample().target());
        assertThat(slow.elapsed()).isGreaterThanOrEqualTo(Duration.ofMillis(1_500));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseProduct.class)
    @DisplayName("a label column missing from the target database is 409 MISMATCH")
    void mismatch(DatabaseProduct product, CapturedOutput output) {
        Env env = envs.get(product);

        Timed mismatch = definition(env.app(), env.member(), env.sample(), "src_mismatch");

        assertProblem(
                mismatch,
                409,
                "BUSINESS_TARGET_DB_MISMATCH",
                Duration.ofSeconds(5),
                env.sample().target());
        assertNoDriverMessages(output, env.sample().target());
    }

    @Test
    @DisplayName("a silent target is 503 UNAVAILABLE by the borrow limit, without the internal database involved")
    void silentTarget() {
        BusinessDataSampleSchema sample = envs.get(DatabaseProduct.POSTGRESQL).sample();
        Target base = sample.target();
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
                            base.password()));
            closeables.add(app);
            app.installDsl(model(sample));
            Actor member = app.reader(settings(sample));

            Timed silentRead = definition(app, member, sample, "src_slow");

            assertProblem(silentRead, 503, "BUSINESS_TARGET_DB_UNAVAILABLE", Duration.ofSeconds(8), base);
            assertThat(app.post(BusinessDataPaths.DEFINITION, member.token(), sample.body("src_slow"))
                            .statusCode())
                    .as("VIEW は対象DB に接続しない")
                    .isEqualTo(200);
        }
    }
}
