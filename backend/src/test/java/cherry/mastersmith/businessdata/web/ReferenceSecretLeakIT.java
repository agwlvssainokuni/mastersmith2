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
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.CELL_MARK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.LABEL_MARK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SEARCH_MARK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_API;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.VALUE_MARK;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.domain.ReferenceCheckResult;
import cherry.mastersmith.businessdata.domain.ReferenceCountState;
import cherry.mastersmith.businessdata.domain.ReferenceValue;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.service.ReferenceLookup;
import cherry.mastersmith.businessdata.service.TableAccessResolution;
import cherry.mastersmith.businessdata.service.TableAccessResolver;
import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.common.error.web.GlobalExceptionHandler;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 他テーブル参照の読み取りの漏えいの防止（Intent 261029-reference-lookup-cleanup の U1、Step 21、BR8.7・BR10.1・BR10.2・BR10.4・BR1.7、
 * NFR1.5・NFR1.15、observability-design.md 2.1、team.md の他テーブル参照の必須のテスト (d)）。PostgreSQL のコンテナ。
 *
 * <p>メソッドの呼び出しの追跡（TRACE）を有効にして、定義の選択肢・検索・ラベルの1件読み・{@code check}・{@code countState} の5つの経路と、
 * 接続できない（503 UNAVAILABLE）・DSL と対象DB の食い違い（409 MISMATCH）・問い合わせの時間切れ（503 TIMEOUT）の失敗を流し、
 * アプリのログと誤りの応答に、参照先の値・ラベル・セル・検索の値・接続先・資格情報・例外のメッセージが無いことを確かめる。失敗の要求ごとに、
 * {@link ReferenceLookup} のロガーの診断の WARN がちょうど1行、{@link GlobalExceptionHandler} の変換の WARN が1行で、どちらも値・
 * メッセージを持たないこと、読み取りで監査の行が増えないことも確かめる。{@code TraceAspect} を外さず本物の Spring の部品で流す（既存の
 * {@code BusinessDataWriteSecretLeakIT} と同じ形）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込みを先、コンテナの実行環境の確かめを後にする（ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
class ReferenceSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** 検索の値の目印（要求に送るだけで、どの行にも当たらない）。 */
    private static final String QUERY_MARK = "zz-ref-query-0d6e";

    /** 対象DB に無い一覧の列（DSL と対象DB の食い違い）。 */
    private static final String GHOST = "ghost";

    /** ログの決めた項目の名前（キーと値で出すもの以外）。 */
    private static final List<String> BASE_KEYS =
            List.of("timestamp", "level", "logger", "thread", "message", "traceId", "spanId");

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private ReferenceTables tables;

    private BusinessDataTestApp app;

    private BusinessDataTestApp refused;

    private Actor member;

    private Actor refusedMember;

    private static DslTable lookupSource(String name, String column, OptionSource options) {
        return table(
                name,
                false,
                List.of("id"),
                column("id").dbType("int4").build(),
                column(column).form(FormPart.LOOKUP).options(options).build());
    }

    private DslModel model() {
        List<DslTable> extra = new ArrayList<>(List.of(tables.apiTables()));
        extra.add(table(
                "tgtghost",
                false,
                List.of("id"),
                column("id").dbType("int4").build(),
                column("v").build(),
                column(GHOST).build()));
        extra.add(table(
                "slow_ref",
                true,
                List.of("id"),
                column("id").dbType("int4").build(),
                column("v").build(),
                column("l").build()));
        extra.add(lookupSource(
                "src_ghost",
                "pick",
                new OptionSource(
                        OptionSourceKind.LOOKUP,
                        List.of(),
                        "tgtghost",
                        "v",
                        null,
                        List.of(new LookupSearchItem("v", SearchOperator.EQUALS)),
                        List.of(new LookupListItem(GHOST, 1)))));
        extra.add(lookupSource(
                "src_slow",
                "pick",
                new OptionSource(
                        OptionSourceKind.LOOKUP,
                        List.of(),
                        "slow_ref",
                        "v",
                        null,
                        List.of(new LookupSearchItem("l", SearchOperator.CONTAINS)),
                        List.of(new LookupListItem("l", 1)))));
        return sample.model(extra.toArray(DslTable[]::new));
    }

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        tables = new ReferenceTables(sample);
        tables.createApi();
        sample.addObjects(
                "CREATE TABLE %stgtghost (id INTEGER NOT NULL PRIMARY KEY, v VARCHAR(40) NULL)",
                "INSERT INTO %stgtghost (id, v) VALUES (1, '" + VALUE_MARK + "')",
                "CREATE VIEW %sslow_ref AS SELECT id, pg_sleep(8)::text AS v, l FROM %stgt",
                "CREATE TABLE %ssrc_ghost (id INTEGER NOT NULL PRIMARY KEY)",
                "CREATE TABLE %ssrc_slow (id INTEGER NOT NULL PRIMARY KEY)");
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target());
        app.installDsl(model());
        member = app.reader(tables.apiSettings(Map.of()));
        BusinessDataTestApp.Target target = sample.target();
        // 接続を拒む番号（ホストの 1 番）へ向けたアプリ（503 UNAVAILABLE の確かめ）。
        refused = BusinessDataTestApp.start(
                dir.resolve("refused"),
                new BusinessDataTestApp.Target(
                        target.product(),
                        target.host(),
                        1,
                        target.database(),
                        target.schema(),
                        target.user(),
                        target.password()));
        refused.installDsl(model());
        refusedMember = refused.reader(tables.apiSettings(Map.of()));
    }

    @AfterAll
    void stop() {
        try {
            if (refused != null) {
                refused.close();
            }
            if (app != null) {
                app.close();
            }
        } finally {
            sample.stop();
        }
    }

    private HttpResponse<String> rows(BusinessDataTestApp target, Actor actor, Map<String, Object> body) {
        return target.post(BusinessDataPaths.REFERENCE_ROWS, actor.token(), body);
    }

    private Map<String, Object> search(String table, String column, String value) {
        return sample.body(
                table, "column", "pick", "conditions", List.of(Map.of("column", column, "value", value)), "page", 1);
    }

    private long auditRows() {
        return app.bean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM audit_events", Long.class);
    }

    /** 1つの失敗の要求の後のログから、2つのロガーの WARN を数える。 */
    private static void assertOneWarningEach(String segment, String label) {
        List<Map<String, Object>> records = JsonLogRecords.parse(segment);
        List<Map<String, Object>> diagnostics = records.stream()
                .filter(r -> ReferenceLookup.class.getName().equals(r.get("logger")))
                .filter(r -> "WARN".equals(r.get("level")))
                .toList();
        List<Map<String, Object>> conversions = records.stream()
                .filter(r -> GlobalExceptionHandler.class.getName().equals(r.get("logger")))
                .filter(r -> "WARN".equals(r.get("level")))
                .toList();
        assertThat(diagnostics).as(label + " の診断の WARN").hasSize(1);
        assertThat(conversions).as(label + " の変換の WARN").hasSize(1);
        assertThat(diagnostics.get(0)).doesNotContainKey("exception");
        assertThat(diagnostics.get(0).keySet())
                .filteredOn(key -> !BASE_KEYS.contains(key))
                .containsAnyOf("purpose", "reason")
                .isSubsetOf("purpose", "reason", "sqlState", "vendorCode", "exceptionClass", "budgetExceeded");
        assertThat(conversions.get(0)).doesNotContainKey("exception");
    }

    @Test
    @DisplayName("with TRACE, the five read paths and the failures leak no value, label, cell, query or message")
    void nothingLeaks(CapturedOutput output) {
        int offset = output.getOut().length();
        long auditBefore = auditRows();
        List<HttpResponse<String>> successes = new ArrayList<>();
        List<HttpResponse<String>> errors = new ArrayList<>();
        List<String> failureSegments = new ArrayList<>();
        LoggingSystem logging = app.bean(LoggingSystem.class);
        logging.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        refused.bean(LoggingSystem.class).setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        ReferenceCheckResult checked;
        ReferenceCountState counted;
        try {
            successes.add(
                    app.post(BusinessDataPaths.DEFINITION, member.token(), sample.body(SRC_API, "purpose", "CREATE")));
            successes.add(rows(app, member, search(SRC_API, "s", SEARCH_MARK)));
            successes.add(rows(app, member, search(SRC_API, "s", QUERY_MARK)));
            successes.add(rows(app, member, sample.body(SRC_API, "column", "ref", "value", VALUE_MARK)));
            TableAccessResolution.Resolved source = (TableAccessResolution.Resolved) app.bean(TableAccessResolver.class)
                    .resolve(member.userId(), new TableQuery(sample.schema(), SRC_API, null));
            ReferenceLookup lookup = app.bean(ReferenceLookup.class);
            TargetReadOutcome<ReferenceCheckResult> check = app.bean(TargetReadAccess.class)
                    .withRead(
                            ReadPurpose.REFERENCE,
                            session -> lookup.check(source, "ref", new ReferenceValue(VALUE_MARK), session));
            TargetReadOutcome<ReferenceCountState> count = app.bean(TargetReadAccess.class)
                    .withRead(ReadPurpose.REFERENCE, session -> lookup.countState(source, "ref", session));
            checked = ((TargetReadOutcome.Done<ReferenceCheckResult>) check).value();
            counted = ((TargetReadOutcome.Done<ReferenceCountState>) count).value();

            List<Runnable> failures = List.of(
                    () -> errors.add(refused.post(
                            BusinessDataPaths.DEFINITION,
                            refusedMember.token(),
                            sample.body(SRC_API, "purpose", "UPDATE"))),
                    () -> errors.add(rows(refused, refusedMember, search(SRC_API, "s", QUERY_MARK))),
                    () -> errors.add(
                            rows(refused, refusedMember, sample.body(SRC_API, "column", "ref", "value", VALUE_MARK))),
                    () -> errors.add(rows(app, member, search("src_ghost", "v", VALUE_MARK))),
                    () -> errors.add(
                            rows(app, member, sample.body("src_ghost", "column", "pick", "value", QUERY_MARK))),
                    () -> errors.add(rows(app, member, search("src_slow", "l", QUERY_MARK))));
            for (Runnable failure : failures) {
                int start = output.getOut().length();
                failure.run();
                failureSegments.add(output.getOut().substring(start));
            }
        } finally {
            logging.setLogLevel(ROOT_LOGGER, null);
            refused.bean(LoggingSystem.class).setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        assertThat(successes).extracting(HttpResponse::statusCode).containsExactly(200, 200, 200, 200);
        assertThat(successes.get(1).body()).contains(VALUE_MARK, LABEL_MARK, CELL_MARK);
        assertThat(checked).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(counted).isEqualTo(new ReferenceCountState.Available());
        assertThat(errors).extracting(HttpResponse::statusCode).containsExactly(503, 503, 503, 409, 409, 503);
        assertThat(errors)
                .extracting(r -> HttpTestClient.json(r).get("code"))
                .containsExactly(
                        "BUSINESS_TARGET_DB_UNAVAILABLE",
                        "BUSINESS_TARGET_DB_UNAVAILABLE",
                        "BUSINESS_TARGET_DB_UNAVAILABLE",
                        "BUSINESS_TARGET_DB_MISMATCH",
                        "BUSINESS_TARGET_DB_MISMATCH",
                        "BUSINESS_TARGET_DB_TIMEOUT");
        for (HttpResponse<String> error : errors) {
            assertThat(error.body())
                    .doesNotContain(VALUE_MARK)
                    .doesNotContain(QUERY_MARK)
                    .doesNotContain(GHOST)
                    .doesNotContain(sample.reader())
                    .doesNotContain("jdbc:")
                    .doesNotContain("Exception")
                    .doesNotContain("canceling statement");
        }
        assertThat(logs)
                .as("メソッドの呼び出しの追跡が有効")
                .contains("ReferenceLookup#", "RowSqlExecutor#", "BusinessDataReadController#");
        JsonLogRecords.assertContainsNoSecret(
                logs,
                VALUE_MARK,
                LABEL_MARK,
                CELL_MARK,
                SEARCH_MARK,
                QUERY_MARK,
                sample.readerPassword(),
                sample.target().host() + ":" + sample.target().port(),
                "jdbc:postgresql",
                "canceling statement",
                "does not exist\"",
                "column \"" + GHOST);
        for (int i = 0; i < failureSegments.size(); i++) {
            assertOneWarningEach(failureSegments.get(i), "失敗 " + i);
        }
        assertThat(auditRows()).as("読み取りは監査に残さない（BR1.7）").isEqualTo(auditBefore);
    }
}
