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
package cherry.mastersmith.dslmanage.web;

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dslmanage.testsupport.DslApi;
import cherry.mastersmith.dslmanage.testsupport.DslAuditRows;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.SilentServer;
import cherry.mastersmith.targetdb.testsupport.TargetDbFixture;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 対象DB を使う DSL の管理の API の結合テスト（PostgreSQL のコンテナ。US1.1 は最初の1種類の DB で確かめる決まり）。
 *
 * <p>生成（201）と照合の警告、応答・ダウンロード・監査・ログに接続先が出ないこと、照合と適用で対象DB が変わらないこと、応答しない
 * 対象DB での生成の 503 と照合の警告（決定 B）、対象DB が使えなくてもほかの操作が使えること（NFR7.8）を確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith({ContainerRuntimeCheck.class, OutputCaptureExtension.class})
class DslTargetDbIT {

    private final TargetDbTestDatabase database = TargetDbTestDatabase.of(DatabaseProduct.POSTGRESQL);

    private final String schema = TargetDbTestDatabase.uniqueName("dsl_");

    private final String otherSchema = TargetDbTestDatabase.uniqueName("other_");

    private final String reader = TargetDbTestDatabase.uniqueName("reader_");

    private final String readerPassword = TestDatabase.randomSecret();

    private Path tempDir;

    private ConfigurableApplicationContext app;

    private DslApi api;

    @BeforeAll
    void start(@TempDir Path dir) {
        tempDir = dir;
        database.start();
        database.createSchema(otherSchema);
        database.createSchema(schema);
        database.createStandardFixture(schema, otherSchema);
        database.createReadOnlyAccount(reader, readerPassword, schema);
        app = startApp(dir.resolve("main"), database.host(), database.port());
        api = adminApi(app);
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
            database.dropAccount(reader);
            database.dropSchema(schema);
            database.dropSchema(otherSchema);
        } finally {
            database.stop();
        }
    }

    private ConfigurableApplicationContext startApp(Path dir, String host, int port) {
        return new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(dir),
                        "--server.port=0",
                        "--mastersmith.auth.password.bcrypt-cost=4",
                        "--mastersmith.target-db.type=postgresql",
                        "--mastersmith.target-db.host=" + host,
                        "--mastersmith.target-db.port=" + port,
                        "--mastersmith.target-db.database=" + database.connectDatabase(schema),
                        "--mastersmith.target-db.schema=" + schema,
                        "--mastersmith.target-db.username=" + reader,
                        "--mastersmith.target-db.password=" + readerPassword);
    }

    private static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    private static DslApi adminApi(ConfigurableApplicationContext context) {
        AdminTestUsers users = new AdminTestUsers(context.getBean(UserAccountService.class), port(context));
        return new DslApi(port(context), users.accessToken(users.createAdmin()));
    }

    /** アプリのログだけを取り出す（テストの道具のログは、コンテナの接続先を出すため除く）。 */
    private static String appLog(CapturedOutput output) {
        StringBuilder log = new StringBuilder();
        for (Map<String, Object> record : JsonLogRecords.parse(output.getAll())) {
            String logger = String.valueOf(record.get("logger"));
            if (logger.startsWith("tc.")
                    || logger.startsWith("org.testcontainers")
                    || logger.startsWith("com.github.dockerjava")) {
                continue;
            }
            log.append(record).append('\n');
        }
        return log.toString();
    }

    private void assertNoConnectionInfo(String text, int port) {
        assertThat(text)
                .doesNotContain(reader)
                .doesNotContain(readerPassword)
                .doesNotContain(database.host() + ":" + port)
                .doesNotContain("jdbc:postgresql");
    }

    /** 対象DB のテーブル・ビューの一覧と、各テーブルの行数。 */
    private Map<String, Long> snapshot() {
        Map<String, Long> result = new TreeMap<>();
        try (Connection connection = database.admin();
                Statement statement = connection.createStatement()) {
            List<String> names = new java.util.ArrayList<>();
            try (ResultSet rs = statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = '" + schema + "'")) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
            for (String name : names) {
                try (ResultSet rs =
                        statement.executeQuery("SELECT COUNT(*) FROM " + database.q(schema) + "." + database.q(name))) {
                    rs.next();
                    result.put(name, rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    @Order(1)
    @DisplayName("loading the schema places the generated DSL, and nothing reveals the connection details")
    void generate(CapturedOutput output) {
        Map<String, Long> before = snapshot();

        HttpResponse<String> generated = api.generate();
        HttpResponse<String> shown = api.get("/preview");
        HttpResponse<byte[]> download = api.getBytes("/preview/download");

        assertThat(generated.statusCode()).isEqualTo(201);
        Map<String, Object> preview = DslApi.json(generated);
        assertThat(preview).containsEntry("source", "GENERATED");
        assertThat(list(preview.get("warnings"))).isEmpty();
        assertThat(list(((Map<String, Object>) preview.get("diff")).get("tables")))
                .extracting(table -> table.get("name"))
                .contains(TargetDbFixture.CUSTOMER, TargetDbFixture.ORDER_ITEMS, TargetDbFixture.CUSTOMER_VIEW);
        assertThat(shown.statusCode()).isEqualTo(200);
        assertThat(list(DslApi.json(shown).get("warnings"))).isEmpty();
        String yaml = new String(download.body(), StandardCharsets.UTF_8);
        DslAuditRows.Row row = new DslAuditRows(app.getBean(JdbcTemplate.class)).last();
        assertThat(row.eventType()).isEqualTo("DSL_GENERATED");
        assertThat(row.dslSource()).isEqualTo("GENERATED");
        assertThat(row.dslHash()).isEqualTo(preview.get("dslHash"));
        int port = database.port();
        assertNoConnectionInfo(generated.body() + shown.body() + yaml + row.all(), port);
        assertNoConnectionInfo(appLog(output), port);
        assertThat(yaml).doesNotContain(schema);

        HttpResponse<String> applied = api.apply((String) preview.get("previewId"));
        assertThat(applied.statusCode()).isEqualTo(200);
        assertThat(snapshot()).as("生成・照合・適用で対象DB は変わらない").isEqualTo(before);
    }

    @Test
    @Order(2)
    @DisplayName("a missing table, a missing column and a type mismatch become warnings, and the preview is placed")
    void reconcileWarnings() {
        byte[] body = DslYaml.dsl()
                .table(
                        TargetDbFixture.CUSTOMER,
                        column(TargetDbFixture.CUSTOMER_ID).type("int4", null, null, null),
                        column("Name").type("varchar", 99, null, null),
                        column("ghost"))
                .table("absent_table", column("c"))
                .bytes();

        HttpResponse<String> placed = api.submit(body, "PASTE");

        assertThat(placed.statusCode()).isEqualTo(201);
        assertThat(list(DslApi.json(placed).get("warnings")))
                .extracting(warning -> warning.get("kind") + " " + warning.get("path"))
                .containsExactly(
                        "TYPE_MISMATCH tables.Customer.columns.Name",
                        "COLUMN_MISSING tables.Customer.columns.ghost",
                        "TABLE_MISSING tables.absent_table");
    }

    @Test
    @Order(3)
    @DisplayName("an unresponsive target makes loading 503 while display warns in time and other operations work")
    void unresponsiveTarget(CapturedOutput output) {
        try (SilentServer silent = SilentServer.start();
                ConfigurableApplicationContext other =
                        startApp(tempDir.resolve("silent"), "127.0.0.1", silent.port())) {
            DslApi otherApi = adminApi(other);

            HttpResponse<String> generated = otherApi.generate();
            String previewId =
                    otherApi.submitOk(DslYaml.dsl().table("t", column("c")).bytes());
            long start = System.nanoTime();
            HttpResponse<String> shown = otherApi.get("/preview");
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(generated.statusCode()).isEqualTo(503);
            assertThat(DslApi.json(generated)).containsEntry("code", "TARGET_DB_UNAVAILABLE");
            assertThat(shown.statusCode()).isEqualTo(200);
            assertThat(list(DslApi.json(shown).get("warnings")))
                    .singleElement()
                    .satisfies(warning -> assertThat(warning).containsEntry("kind", "TARGET_UNAVAILABLE"));
            assertThat(elapsed).as("接続の待ち 3 秒で打ち切り、10 秒の内に警告つきで返る").isLessThan(10_000L);
            assertThat(otherApi.get("/status").statusCode()).isEqualTo(200);
            assertThat(otherApi.getBytes("/preview/download").statusCode()).isEqualTo(200);
            assertThat(otherApi.apply(previewId).statusCode()).isEqualTo(200);
            assertThat(otherApi.get("/history").statusCode()).isEqualTo(200);
            otherApi.submitOk(DslYaml.dsl().table("u", column("c")).bytes());
            assertThat(otherApi.discard().statusCode()).isEqualTo(204);
            assertNoConnectionInfo(generated.body() + shown.body() + appLog(output), silent.port());
        }
    }
}
