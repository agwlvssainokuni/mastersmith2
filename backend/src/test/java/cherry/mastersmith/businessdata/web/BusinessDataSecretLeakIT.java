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

import cherry.mastersmith.businessdata.service.BusinessDataReadService;
import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.RandomFieldMask;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
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

/**
 * 業務データの読み取りの漏えいの防止（NFR4.2・NFR8.2、BR10.1〜BR10.3、NFR 設計のレビュー R-02、team.md の「漏えい」）。PostgreSQL の
 * コンテナ。メソッドの呼び出しの追跡（TRACE）を有効にして、一覧・詳細・400・404・503・PostgreSQL の 22P02 を起こす OTHER の値・不正な数と
 * 日付の文字を送り、アプリのログと誤りの応答に行の値・主キーの値・検索の値・接続先・資格情報・例外のメッセージが無いことを確かめる。
 * {@code TraceAspect} を外さず、本物の Spring の部品で流す（既存の {@code *SecretLeakIT} と同じ形）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込みを先、コンテナの実行環境の確かめを後にする（ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
class BusinessDataSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** 検索の値の目印。 */
    private static final String SEARCH_MARK = "zz-leak-search-5e1f";

    /** 主キーの値の目印。 */
    private static final String KEY_MARK = "98765431";

    /** 要求のカラムの名前の目印（DSL に無い名前。応答にもログにも出ない）。 */
    private static final String NAME_MARK = "zz-leak-name-3c9d";

    /** OTHER の値の目印（uuid として読めない値。PostgreSQL の 22P02 の文に入る）。 */
    private static final String OTHER_MARK = "zz-leak-uuid-77ab";

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private BusinessDataTestApp refused;

    private Actor member;

    private Actor refusedMember;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target());
        app.installDsl(sample.model());
        member = app.reader(sample.settings(MainPermission.READ));
        BusinessDataTestApp.Target target = sample.target();
        // 接続を拒む番号（コンテナの番号を閉じたホストの 1 番）へ向けたアプリ（503 の確かめ）。
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
        refused.installDsl(sample.model());
        refusedMember = refused.reader(sample.settings(MainPermission.READ));
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

    @Test
    @DisplayName("with TRACE, a long or odd name of a condition, a sort column or a key column is not echoed in the 400"
            + " nor written to the logs (R-07)")
    void unknownNamesLeaveNoTrace(CapturedOutput output) {
        int offset = output.getOut().length();
        String longName = NAME_MARK + "n".repeat(60 * 1000);
        // 対象の名前の長さは Jackson の上限（50,000 文字）に収まる長さにする（主キーの対応表の名前）。
        String keyName = NAME_MARK + "k".repeat(40_000);
        String oddName = NAME_MARK + "'; DROP TABLE item; --";
        List<HttpResponse<String>> responses = new ArrayList<>();
        LoggingSystem logging = app.bean(LoggingSystem.class);
        logging.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        try {
            // 本文の上限は 64 KB のため、長い名前は 1 つの要求に 1 つだけ入れる。
            responses.add(app.post(
                    BusinessDataPaths.SEARCH,
                    member.token(),
                    sample.body("item", "conditions", List.of(Map.of("column", longName, "value", "x")))));
            responses.add(app.post(
                    BusinessDataPaths.SEARCH,
                    member.token(),
                    sample.body(
                            "item",
                            "conditions",
                            List.of(Map.of("column", oddName, "value", "x")),
                            "sort",
                            Map.of("column", oddName, "direction", "ASC"))));
            responses.add(app.post(
                    BusinessDataPaths.SEARCH,
                    member.token(),
                    sample.body("item", "sort", Map.of("column", longName, "direction", "ASC"))));
            responses.add(app.post(
                    BusinessDataPaths.GET,
                    member.token(),
                    sample.body("item", "key", Map.of("id", "1", keyName, "1"))));
            responses.add(app.post(
                    BusinessDataPaths.GET,
                    member.token(),
                    sample.body("item", "key", Map.of("id", "1", oddName, "1"))));
        } finally {
            logging.setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(400, 400, 400, 400, 400);
        for (HttpResponse<String> response : responses) {
            assertThat(response.body()).doesNotContain(NAME_MARK).hasSizeLessThan(4_000);
            assertThat(HttpTestClient.json(response)).containsKey("fieldErrors");
        }
        assertThat(logs).as("メソッドの呼び出しの追跡が有効").contains("BusinessDataReadController#");
        assertThat(logs).doesNotContain(NAME_MARK);
    }

    @Test
    @DisplayName("with TRACE, list, detail, 400, 404, 503 and 22P02 leak no value, connection or exception message")
    void nothingLeaks(CapturedOutput output) {
        int offset = output.getOut().length();
        List<HttpResponse<String>> errors = new ArrayList<>();
        HttpResponse<String> list;
        HttpResponse<String> detail;
        LoggingSystem logging = app.bean(LoggingSystem.class);
        logging.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        try {
            list = app.post(
                    BusinessDataPaths.SEARCH,
                    member.token(),
                    sample.body(
                            "item",
                            "conditions",
                            List.of(Map.of("column", "name", "value", SEARCH_MARK)),
                            "page",
                            1,
                            "size",
                            20));
            app.post(BusinessDataPaths.SEARCH, member.token(), sample.body("item", "page", 1, "size", 20));
            detail = app.post(BusinessDataPaths.GET, member.token(), sample.body("item", "key", Map.of("id", "1")));
            errors.add(app.post(
                    BusinessDataPaths.SEARCH,
                    member.token(),
                    sample.body("item", "conditions", List.of(Map.of("column", "id", "value", SEARCH_MARK + "12a")))));
            errors.add(app.post(
                    BusinessDataPaths.SEARCH,
                    member.token(),
                    sample.body(
                            "item",
                            "conditions",
                            List.of(Map.of("column", "price", "from", "1e5" + SEARCH_MARK, "to", "2026-13-01")))));
            errors.add(app.post(
                    BusinessDataPaths.GET, member.token(), sample.body("item", "key", Map.of("id", KEY_MARK))));
            errors.add(app.post(
                    BusinessDataPaths.SEARCH,
                    member.token(),
                    sample.body("item", "conditions", List.of(Map.of("column", "tag", "value", OTHER_MARK)))));
            errors.add(refused.post(BusinessDataPaths.SEARCH, refusedMember.token(), sample.body("item")));
            errors.add(refused.post(
                    BusinessDataPaths.GET, refusedMember.token(), sample.body("item", "key", Map.of("id", KEY_MARK))));
        } finally {
            logging.setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
        assertThat(detail.body()).as("応答は値を返す").contains(BusinessDataSampleSchema.SECRET_NAME);
        assertThat(errors).extracting(HttpResponse::statusCode).containsExactly(400, 400, 404, 400, 503, 503);
        assertThat(HttpTestClient.json(errors.get(3))).as("22P02 は値を出さずに 400").doesNotContainKey("fieldErrors");
        for (HttpResponse<String> error : errors) {
            // 8 桁の数字の目印が乱数のトレースID と type の URL の番号に偶然に当たらないよう、伏せてから確かめる（U3 の B6、N-32）。
            assertThat(RandomFieldMask.mask(error.body()))
                    .doesNotContain(SEARCH_MARK)
                    .doesNotContain(KEY_MARK)
                    .doesNotContain(OTHER_MARK)
                    .doesNotContain(sample.reader())
                    .doesNotContain("jdbc:")
                    .doesNotContain("Exception");
        }
        assertThat(logs)
                .as("メソッドの呼び出しの追跡が有効")
                .contains("BusinessDataReadService#", "RowSqlExecutor#", "BusinessDataReadController#");
        assertThat(logs).as("Done を通る戻り値は型の名前だけ").contains("Done[valueType=");
        JsonLogRecords.assertContainsNoSecret(
                RandomFieldMask.mask(logs),
                BusinessDataSampleSchema.SECRET_NAME,
                "ノート",
                SEARCH_MARK,
                KEY_MARK,
                OTHER_MARK,
                sample.reader(),
                sample.readerPassword(),
                sample.target().host() + ":" + sample.target().port(),
                "jdbc:postgresql",
                "invalid input syntax");
        List<Map<String, Object>> warns = JsonLogRecords.parse(output.getOut().substring(offset)).stream()
                .filter(r -> BusinessDataReadService.class.getName().equals(r.get("logger")))
                .filter(r -> "WARN".equals(r.get("level")))
                .toList();
        assertThat(warns).as("22P02 と 503 の2つで WARN").hasSizeGreaterThanOrEqualTo(3);
        assertThat(warns).allSatisfy(record -> {
            assertThat(record).doesNotContainKey("exception");
            assertThat(record.keySet())
                    .filteredOn(
                            key -> !List.of("timestamp", "level", "logger", "thread", "message", "traceId", "spanId")
                                    .contains(key))
                    .containsOnly("purpose", "reason", "sqlState", "vendorCode", "exceptionClass");
        });
    }
}
