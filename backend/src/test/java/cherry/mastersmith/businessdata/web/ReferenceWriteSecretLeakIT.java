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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_MAIN;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_MM;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_SLOW;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_TGT;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.service.BusinessDataWriteService;
import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.RandomFieldMask;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.common.error.web.GlobalExceptionHandler;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 保存前の参照の確かめの漏えいの防止と診断の WARN の1行（Intent 261029-reference-lookup-cleanup の U2、計画の Step 10、(d)、NFR1.30・
 * NFR1.32、BR7.1、AC3.3.2）。PostgreSQL のコンテナ。メソッドの呼び出しの追跡（TRACE）を有効にして、架空の目印を参照の値・参照先の行・
 * 本文に入れ、成功・409・400・TIMEOUT・MISMATCH・UNAVAILABLE を流し、ログ・応答・監査の行に目印が無いことを確かめる。
 *
 * <p>NFR1.32 は承認の場の読み方（observability-design.md「承認の場の決定」）のとおり、{@link BusinessDataWriteService} のロガーの
 * 診断の WARN が失敗ごとにちょうど1行（{@code purpose=REFERENCE}、{@code WRITE} の行が無い）、{@link GlobalExceptionHandler} の
 * ロガーの変換の WARN は別の1行として数える。拒否と 400 では診断の WARN を出さない。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込みを先、コンテナの実行環境の確かめを後にする（ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
class ReferenceWriteSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** 参照先の行の値の目印。 */
    private static final String TARGET_MARK = "zz-refw-target-3f81";

    /** 本文の参照の値の目印（参照先に無い）。 */
    private static final String BODY_MARK = "zz-refw-body-a24e";

    /** 診断の WARN に出してよいキー。 */
    private static final List<String> DIAGNOSTIC_KEYS =
            List.of("purpose", "stage", "reason", "sqlState", "vendorCode", "exceptionClass", "budgetExceeded");

    /** ログの1行に共通のキー。 */
    private static final List<String> COMMON_KEYS =
            List.of("timestamp", "level", "logger", "thread", "message", "traceId", "spanId");

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private BusinessDataTestApp refused;

    private Actor member;

    private Actor refusedMember;

    private int nextId = 5_000;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        ReferenceTables tables = new ReferenceTables(sample);
        tables.createWrite();
        tables.createFailing();
        sample.addObjects(
                "INSERT INTO %s" + W_TGT + " (id, v, l) VALUES (90, '" + TARGET_MARK + "', '" + TARGET_MARK + "')");
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[0]);
        app.installDsl(sample.model(tables.failingTables()));
        member = app.reader(tables.writeSettings(Map.of()));
        BusinessDataTestApp.Target target = sample.target();
        refused = BusinessDataTestApp.start(
                dir.resolve("refused"),
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
        refused.installDsl(sample.model(tables.failingTables()));
        refusedMember = refused.reader(tables.writeSettings(Map.of()));
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

    private HttpResponse<String> create(
            BusinessDataTestApp target, Actor actor, String table, Map<String, String> values) {
        Map<String, Object> body = sample.body(table);
        body.put("definitionId", target.definitionId(actor.token(), sample.schema(), table));
        body.put("values", values);
        return target.post(BusinessDataPaths.ROWS, actor.token(), body);
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private String id() {
        return Integer.toString(++nextId);
    }

    /** 1要求の応答と、その間のログ。 */
    private record Sent(HttpResponse<String> response, String logs) {

        List<Map<String, Object>> warns(Class<?> logger) {
            return JsonLogRecords.parse(logs).stream()
                    .filter(r -> logger.getName().equals(r.get("logger")))
                    .filter(r -> "WARN".equals(r.get("level")))
                    .toList();
        }
    }

    private static Sent send(CapturedOutput output, Supplier<HttpResponse<String>> request) {
        int offset = output.getOut().length();
        HttpResponse<String> response = request.get();
        return new Sent(response, output.getOut().substring(offset));
    }

    @Test
    @DisplayName("with TRACE, checks leak no reference value and each failure logs exactly one diagnostic warning")
    void nothingLeaksAndOneWarning(CapturedOutput output) {
        LoggingSystem logging = app.bean(LoggingSystem.class);
        LoggingSystem refusedLogging = refused.bean(LoggingSystem.class);
        logging.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        refusedLogging.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        int start = output.getOut().length();
        List<Sent> rejections = new ArrayList<>();
        List<Sent> failures = new ArrayList<>();
        Sent success;
        try {
            success = send(output, () -> create(app, member, WS_MAIN, values("id", id(), "ref", TARGET_MARK)));
            rejections.add(send(output, () -> create(app, member, WS_MAIN, values("id", id(), "ref", BODY_MARK))));
            rejections.add(send(output, () -> create(app, member, WS_MAIN, values("id", BODY_MARK, "ref", BODY_MARK))));
            failures.add(send(output, () -> create(app, member, WS_SLOW, values("id", id(), "a", BODY_MARK))));
            failures.add(send(output, () -> create(app, member, WS_MM, values("id", id(), "m", BODY_MARK))));
            failures.add(
                    send(output, () -> create(refused, refusedMember, WS_MAIN, values("id", id(), "ref", BODY_MARK))));
        } finally {
            logging.setLogLevel(ROOT_LOGGER, null);
            refusedLogging.setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(start) + output.getErr();

        assertThat(success.response().statusCode())
                .as(success.response().body())
                .isEqualTo(201);
        assertThat(rejections).extracting(s -> s.response().statusCode()).containsExactly(409, 400);
        assertThat(failures).extracting(s -> s.response().statusCode()).containsExactly(503, 409, 503);
        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("BusinessDataWriteService#", "ReferenceLookup#");
        for (Sent sent : List.of(
                success, rejections.get(0), rejections.get(1), failures.get(0), failures.get(1), failures.get(2))) {
            assertThat(RandomFieldMask.mask(sent.response().body()))
                    .doesNotContain(TARGET_MARK)
                    .doesNotContain(BODY_MARK)
                    .doesNotContain("Exception")
                    .doesNotContain("jdbc:");
        }
        JsonLogRecords.assertContainsNoSecret(
                RandomFieldMask.mask(logs),
                TARGET_MARK,
                BODY_MARK,
                sample.writer().user(),
                sample.writer().password(),
                sample.readerPassword(),
                sample.target().host() + ":" + sample.target().port(),
                "jdbc:postgresql",
                "canceling statement");
        assertThat(success.warns(BusinessDataWriteService.class)).isEmpty();
        for (Sent sent : rejections) {
            assertThat(sent.warns(BusinessDataWriteService.class))
                    .as("拒否と 400 は診断の WARN を出さない")
                    .isEmpty();
        }
        for (Sent sent : failures) {
            assertThat(sent.warns(BusinessDataWriteService.class))
                    .singleElement()
                    .satisfies(record -> {
                        assertThat(record).containsEntry("purpose", "REFERENCE").doesNotContainKey("exception");
                        assertThat(record.keySet())
                                .filteredOn(key -> !COMMON_KEYS.contains(key))
                                .isSubsetOf(DIAGNOSTIC_KEYS)
                                .contains("purpose", "stage", "reason", "budgetExceeded");
                    });
            assertThat(sent.warns(GlobalExceptionHandler.class))
                    .as("変換の WARN は既存の別の1行")
                    .hasSize(1);
        }
        assertThat(failures.get(0).warns(BusinessDataWriteService.class).getFirst())
                .containsEntry("stage", "CHECK")
                .containsEntry("reason", "TIMEOUT");
        assertThat(failures.get(1).warns(BusinessDataWriteService.class).getFirst())
                .containsEntry("reason", "MISMATCH");
        assertThat(failures.get(2).warns(BusinessDataWriteService.class).getFirst())
                .containsEntry("stage", "BORROW")
                .containsEntry("reason", "UNAVAILABLE");
        JdbcTemplate jdbc = app.bean(JdbcTemplate.class);
        List<Map<String, Object>> audits = jdbc.queryForList("SELECT * FROM audit_events");
        assertThat(audits.toString()).doesNotContain(TARGET_MARK).doesNotContain(BODY_MARK);
    }
}
