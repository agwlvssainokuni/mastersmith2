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

import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig;
import cherry.mastersmith.audit.testsupport.FailingAuditEventRepositoryConfig.FailingAuditEventRepository;
import cherry.mastersmith.businessdata.service.BusinessDataWriteService;
import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 業務データの書き込みの漏えいの防止（Intent 261016-business-data-views の U2、計画の Step 23、BR12.1〜BR12.4、NFR4.2・NFR8.4、
 * team.md の「漏えい」、project.md の Forbidden）。PostgreSQL のコンテナ。メソッドの呼び出しの追跡（TRACE）を有効にして、作成・更新・
 * 削除・一意と外部キーの違反（違反の後の確かめを通る）・CHECK の拒否・接続できない 503・監査の書き込みの失敗・非検査の例外になる値（不正な
 * 数・日時の文字）を流し、アプリのログと誤りの応答に、行の値・主キーの値・接続先・資格情報・例外のメッセージが無く、ログのキーが決めた名前
 * だけであることを確かめる。{@code TraceAspect} を外さず、本物の Spring の部品で流す（既存の {@code *SecretLeakIT} と同じ形）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込みを先、コンテナの実行環境の確かめを後にする（ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
class BusinessDataWriteSecretLeakIT {

    private static final String ROOT_LOGGER = "cherry.mastersmith";

    /** 違反と拒否を起こす表。 */
    private static final String LEAKY = "leaky";

    /** 値の目印。 */
    private static final String VALUE_MARK = "zz-leak-value-81c2";

    /** 主キーの値の目印。 */
    private static final String KEY_MARK = "97531864";

    /** 一意の値の目印（DB の一意の制約にだけ当たる）。 */
    private static final String UNIQUE_MARK = "zz-leak-unique-4d70";

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private BusinessDataTestApp refused;

    private Actor member;

    private Actor refusedMember;

    private FailingAuditEventRepository failing;

    private DslModel model() {
        return sample.model(tableWithForeignKeys(
                LEAKY,
                List.of("id"),
                List.of(new DslForeignKey(List.of("item_id"), ITEM, List.of("id"))),
                column("id").dbType("int4").build(),
                // DSL では unique にしない（DB の制約の違反と、違反の後の確かめの経路を通す）。
                column("code").length(40).build(),
                column("item_id").dbType("int4").build(),
                column("qty").dbType("int4").build(),
                column("at").dbType("timestamp").build(),
                column("v").length(50).build()));
    }

    private Map<PermissionTarget, PermissionValues> settings() {
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(sample.schema()), new PermissionValues(MainPermission.FULL, true, true));
        return settings;
    }

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        sample.addObjects("CREATE TABLE %s" + LEAKY + " (id INTEGER NOT NULL PRIMARY KEY,"
                + " code VARCHAR(40) NULL UNIQUE, item_id INTEGER NULL REFERENCES %s" + ITEM + " (id),"
                + " qty INTEGER NULL CHECK (qty >= 0), at TIMESTAMP NULL, v VARCHAR(50) NULL)");
        sample.database()
                .execute("INSERT INTO " + sample.database().q(sample.schema()) + "." + LEAKY + " (id, code) VALUES ("
                        + KEY_MARK + ", '" + UNIQUE_MARK + "')");
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[] {
            FailingAuditEventRepositoryConfig.class
        });
        app.installDsl(model());
        member = app.reader(settings());
        failing = app.bean(FailingAuditEventRepository.class);
        failing.mode(FailingAuditEventRepositoryConfig.Mode.NONE);
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
                        target.password()),
                sample.writer(),
                new Class<?>[0]);
        refused.installDsl(model());
        refusedMember = refused.reader(settings());
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

    private Map<String, Object> body(BusinessDataTestApp target, Actor actor) {
        Map<String, Object> body = sample.body(LEAKY);
        body.put("definitionId", target.definitionId(actor.token(), sample.schema(), LEAKY));
        return body;
    }

    private HttpResponse<String> create(Map<String, String> values) {
        Map<String, Object> body = body(app, member);
        body.put("values", values);
        return app.post(BusinessDataPaths.ROWS, member.token(), body);
    }

    @Test
    @DisplayName("with TRACE, writes, violations, rejections, 503 and audit failures leak no value, key or message")
    void nothingLeaks(CapturedOutput output) {
        int offset = output.getOut().length();
        List<HttpResponse<String>> successes = new ArrayList<>();
        List<HttpResponse<String>> errors = new ArrayList<>();
        LoggingSystem logging = app.bean(LoggingSystem.class);
        logging.setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        refused.bean(LoggingSystem.class).setLogLevel(ROOT_LOGGER, LogLevel.TRACE);
        try {
            successes.add(create(Map.of("id", "1" + KEY_MARK.substring(1), "v", VALUE_MARK)));
            Map<String, Object> update = body(app, member);
            update.put("key", Map.of("id", "1" + KEY_MARK.substring(1)));
            update.put("values", Map.of("v", VALUE_MARK + "-u"));
            successes.add(app.patch(BusinessDataPaths.ROWS, member.token(), update));
            Map<String, Object> delete = body(app, member);
            delete.put("key", Map.of("id", "1" + KEY_MARK.substring(1)));
            successes.add(app.post(BusinessDataPaths.DELETE, member.token(), delete));
            // 一意の違反（主キーと DB の一意の制約）。違反の後の確かめを通る。
            errors.add(create(Map.of("id", KEY_MARK, "v", VALUE_MARK)));
            errors.add(create(Map.of("id", "2" + KEY_MARK.substring(1), "code", UNIQUE_MARK)));
            // 外部キーの違反と CHECK の拒否。
            errors.add(create(Map.of("id", "3" + KEY_MARK.substring(1), "item_id", KEY_MARK)));
            errors.add(create(Map.of("id", "4" + KEY_MARK.substring(1), "qty", "-" + KEY_MARK)));
            // 非検査の例外になりうる値（不正な数・日時の文字）。
            errors.add(create(Map.of("id", VALUE_MARK)));
            errors.add(create(Map.of("id", "5" + KEY_MARK.substring(1), "at", "2026-13-45T" + VALUE_MARK)));
            // 接続できない対象DB。
            Map<String, Object> refusedBody = body(refused, refusedMember);
            refusedBody.put("values", Map.of("id", KEY_MARK, "v", VALUE_MARK));
            errors.add(refused.post(BusinessDataPaths.ROWS, refusedMember.token(), refusedBody));
            // 監査の書き込みの失敗（操作は成功）。
            failing.mode(FailingAuditEventRepositoryConfig.Mode.APPEND_FAILURE);
            try {
                successes.add(create(Map.of("id", "6" + KEY_MARK.substring(1), "v", VALUE_MARK)));
            } finally {
                failing.mode(FailingAuditEventRepositoryConfig.Mode.NONE);
            }
        } finally {
            logging.setLogLevel(ROOT_LOGGER, null);
            refused.bean(LoggingSystem.class).setLogLevel(ROOT_LOGGER, null);
        }
        String logs = output.getOut().substring(offset) + output.getErr();

        assertThat(successes).extracting(HttpResponse::statusCode).containsExactly(201, 200, 200, 201);
        assertThat(errors).extracting(HttpResponse::statusCode).containsExactly(409, 409, 409, 409, 400, 400, 503);
        for (HttpResponse<String> error : errors) {
            assertThat(error.body())
                    .doesNotContain(VALUE_MARK)
                    .doesNotContain(KEY_MARK)
                    .doesNotContain(UNIQUE_MARK)
                    .doesNotContain(sample.writer().user())
                    .doesNotContain("jdbc:")
                    .doesNotContain("Exception")
                    .doesNotContain("duplicate key");
        }
        for (HttpResponse<String> success : successes) {
            assertThat(success.body()).doesNotContain(VALUE_MARK);
        }
        assertThat(logs)
                .as("メソッドの呼び出しの追跡が有効")
                .contains("BusinessDataWriteService#", "RowSqlExecutor#", "BusinessDataWriteController#");
        JsonLogRecords.assertContainsNoSecret(
                logs,
                VALUE_MARK,
                KEY_MARK,
                KEY_MARK.substring(1),
                UNIQUE_MARK,
                sample.writer().user(),
                sample.writer().password(),
                sample.readerPassword(),
                sample.target().host() + ":" + sample.target().port(),
                "jdbc:postgresql",
                "duplicate key value",
                "violates",
                "Key (");
        List<Map<String, Object>> warns = JsonLogRecords.parse(output.getOut().substring(offset)).stream()
                .filter(r -> BusinessDataWriteService.class.getName().equals(r.get("logger")))
                .filter(r -> "WARN".equals(r.get("level")))
                .toList();
        assertThat(warns).as("CHECK と 503 で WARN").hasSizeGreaterThanOrEqualTo(2);
        assertThat(warns).allSatisfy(record -> {
            assertThat(record).doesNotContainKey("exception");
            assertThat(record.keySet())
                    .filteredOn(
                            key -> !List.of("timestamp", "level", "logger", "thread", "message", "traceId", "spanId")
                                    .contains(key))
                    .containsOnly("purpose", "stage", "reason", "sqlState", "vendorCode", "exceptionClass");
        });
    }
}
