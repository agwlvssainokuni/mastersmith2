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
package cherry.mastersmith.dslmanage.service;

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import cherry.mastersmith.dslmanage.testsupport.DslApi;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.dslmanage.testsupport.DslYamlHashes;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 起動時の適用中の DSL の読み込み（BR5.1・BR5.2、AC4.1.5・AC4.1.6、NFR8.4）の結合テスト。同じ内部DB でアプリを起動し直して確かめる。
 */
@ExtendWith(OutputCaptureExtension.class)
class DslStartupIT {

    private static ConfigurableApplicationContext start(Path dir) {
        return new SpringApplicationBuilder(MastersmithApplication.class)
                .run(
                        "--spring.datasource.url=" + TestDatabase.url(dir),
                        "--server.port=0",
                        "--mastersmith.auth.password.bcrypt-cost=4");
    }

    private static ActiveDsl current(ConfigurableApplicationContext app) {
        return app.getBean(ActiveDslModelProvider.class).current();
    }

    @Test
    @DisplayName("without history the app starts with no applied DSL, and after a restart the applied DSL is back")
    void restartKeepsTheAppliedDsl(@TempDir Path dir) {
        byte[] body = DslYaml.dsl().table("kept", column("c")).bytes();
        String hash;
        try (ConfigurableApplicationContext first = start(dir)) {
            assertThat(current(first)).isInstanceOf(ActiveDsl.Absent.class);
            int port = Integer.parseInt(first.getEnvironment().getProperty("local.server.port"));
            AdminTestUsers users = new AdminTestUsers(first.getBean(UserAccountService.class), port);
            DslApi api = new DslApi(port, users.accessToken(users.createAdmin()));
            assertThat(api.apply(api.submitOk(body)).statusCode()).isEqualTo(200);
            hash = ((ActiveDsl.Present) current(first)).dslHash();
        }

        try (ConfigurableApplicationContext second = start(dir)) {
            assertThat(current(second)).isInstanceOfSatisfying(ActiveDsl.Present.class, present -> {
                assertThat(present.dslHash()).isEqualTo(hash);
                assertThat(present.model().schema().tables()).containsOnlyKeys("kept");
            });
        }
    }

    private static int port(ConfigurableApplicationContext app) {
        return Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
    }

    private static void storeApplied(ConfigurableApplicationContext app, byte[] body, String hash) {
        app.getBean(JdbcTemplate.class)
                .update(
                        "INSERT INTO dsl_applied_revisions (revision_id, yaml_bytes, dsl_hash, source,"
                                + " applied_by_user_id, applied_at) VALUES (?, ?, ?, 'UPLOAD', 1, ?)",
                        UUID.randomUUID(),
                        body,
                        hash,
                        java.sql.Timestamp.from(Instant.parse("2026-09-24T00:00:00Z")));
    }

    @Test
    @DisplayName("an applied DSL of version 1 logs one error, the app starts healthy without an applied DSL and the"
            + " status marks it unreadable")
    void unreadableAppliedDsl(@TempDir Path dir, CapturedOutput output) {
        try (ConfigurableApplicationContext first = start(dir)) {
            storeApplied(first, "version: 1\nbody_marker_text: 1\n".getBytes(StandardCharsets.UTF_8), "f".repeat(64));
        }

        try (ConfigurableApplicationContext second = start(dir)) {
            assertThat(current(second)).isInstanceOf(ActiveDsl.Absent.class);
            assertThat(second.isRunning()).isTrue();
            assertThat(new HttpTestClient(port(second)).get("/actuator/health").statusCode())
                    .isEqualTo(200);
            AdminTestUsers users = new AdminTestUsers(second.getBean(UserAccountService.class), port(second));
            DslApi api = new DslApi(port(second), users.accessToken(users.createAdmin()));
            assertThat(DslApi.json(api.get("/status"))).containsEntry("appliedUnreadable", true);
        }
        List<Map<String, Object>> errors = JsonLogRecords.parse(output.getAll()).stream()
                .filter(record -> String.valueOf(record.get("message")).equals(DslStartupLoader.INVALID_MESSAGE))
                .toList();
        assertThat(errors).singleElement().satisfies(record -> {
            assertThat(record).containsEntry("level", "ERROR").containsEntry("dsl.hash", "ffffffffffff");
            assertThat(String.valueOf(record)).contains("UNSUPPORTED_VERSION").doesNotContain("body_marker_text");
        });
    }

    @Test
    @DisplayName("an applied DSL with a menu of depth 6 starts with the too deep branch pruned and one warning without"
            + " names")
    void tooDeepAppliedDsl(@TempDir Path dir, CapturedOutput output) {
        byte[] body = DslYaml.dsl()
                .table("marker_table_text", column("c"))
                .menu("目印の表示名", "Marker label", "marker_table_text")
                .deepMenu(6, "marker_table_text")
                .bytes();
        String hash = DslYamlHashes.sha256(body);
        try (ConfigurableApplicationContext first = start(dir)) {
            storeApplied(first, body, hash);
        }

        try (ConfigurableApplicationContext second = start(dir)) {
            assertThat(current(second)).isInstanceOfSatisfying(ActiveDsl.Present.class, present -> {
                assertThat(present.dslHash()).isEqualTo(hash);
                assertThat(present.model().menus())
                        .singleElement()
                        .satisfies(item -> assertThat(item.label().ja()).isEqualTo("目印の表示名"));
            });
            AdminTestUsers users = new AdminTestUsers(second.getBean(UserAccountService.class), port(second));
            DslApi api = new DslApi(port(second), users.accessToken(users.createAdmin()));
            assertThat(DslApi.json(api.get("/status"))).containsEntry("appliedUnreadable", false);
        }
        List<Map<String, Object>> warnings = JsonLogRecords.parse(output.getAll()).stream()
                .filter(record -> String.valueOf(record.get("message")).equals(DslStartupLoader.PRUNED_MESSAGE))
                .toList();
        assertThat(warnings).singleElement().satisfies(record -> {
            assertThat(record)
                    .containsEntry("level", "WARN")
                    .containsEntry("dsl.hash", hash.substring(0, 12))
                    .containsKey("dsl.prunedMenuItems")
                    .containsKey("dsl.menuDepthLimit");
            assertThat(String.valueOf(record.get("dsl.prunedMenuItems"))).isEqualTo("6");
            assertThat(String.valueOf(record.get("dsl.menuDepthLimit"))).isEqualTo("5");
        });
        assertThat(output.getAll()).doesNotContain("marker_table_text").doesNotContain("目印の表示名");
    }
}
