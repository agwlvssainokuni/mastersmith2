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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_API;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.TGT;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 参照先の検索とラベルの1件読みの API の認可の表（Intent 261029-reference-lookup-cleanup の U1、Step 21、BR1.3・BR3.1・BR3.3・BR3.4・
 * BR11.1、team.md の他テーブル参照の必須のテスト (a)）。PostgreSQL のコンテナ。403 を確かめる利用者は、スキーマを FULL にしたうえで
 * 要る権限を1つだけ欠く（参照元のカラムが FULL でない・参照先の表が NONE・値のカラムが NONE・ラベルのカラムが NONE・一覧の列が NONE・
 * 使える検索の条件が残らない）。値のカラムがバイナリ・password の参照も 403。停止中の利用者は 401。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceAuthorizationApiIT {

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private ReferenceTables tables;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        tables = new ReferenceTables(sample);
        tables.createApi();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target());
        app.installDsl(sample.model(tables.apiTables()));
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
        } finally {
            sample.stop();
        }
    }

    /** 要求の種類。 */
    enum Mode {
        SEARCH,
        VALUE
    }

    /** 主体（要る権限のどれを欠くか）。 */
    enum Subject {
        ANONYMOUS,
        SUSPENDED,
        FULL,
        SOURCE_COLUMN_READ,
        TARGET_TABLE_NONE,
        VALUE_COLUMN_NONE,
        LABEL_COLUMN_NONE,
        LIST_COLUMN_NONE,
        NO_USABLE_SEARCH,
        BINARY_VALUE,
        PASSWORD_VALUE
    }

    static Stream<Arguments> table() {
        Stream.Builder<Arguments> rows = Stream.builder();
        for (Mode mode : Mode.values()) {
            for (Subject subject : Subject.values()) {
                int status =
                        switch (subject) {
                            case ANONYMOUS, SUSPENDED -> 401;
                            case FULL -> 200;
                            default -> 403;
                        };
                String code =
                        switch (status) {
                            case 401 -> "AUTHENTICATION_REQUIRED";
                            case 403 -> "ACCESS_DENIED";
                            default -> null;
                        };
                rows.add(Arguments.of(mode, subject, status, code));
            }
        }
        return rows.build();
    }

    private Map<PermissionTarget, PermissionValues> settings(Subject subject) {
        String schema = sample.schema();
        PermissionValues none = new PermissionValues(MainPermission.NONE, null, null);
        Map<PermissionTarget, PermissionValues> overrides =
                switch (subject) {
                    case SOURCE_COLUMN_READ ->
                        Map.of(
                                PermissionTarget.column(schema, SRC_API, "pick"),
                                new PermissionValues(MainPermission.READ, null, null),
                                PermissionTarget.column(schema, SRC_API, "pickbin"),
                                new PermissionValues(MainPermission.READ, null, null),
                                PermissionTarget.column(schema, SRC_API, "pickpw"),
                                new PermissionValues(MainPermission.READ, null, null));
                    case TARGET_TABLE_NONE -> Map.of(PermissionTarget.table(schema, TGT), none);
                    case VALUE_COLUMN_NONE -> Map.of(PermissionTarget.column(schema, TGT, "v"), none);
                    case LABEL_COLUMN_NONE -> Map.of(PermissionTarget.column(schema, TGT, "l"), none);
                    case LIST_COLUMN_NONE -> Map.of(PermissionTarget.column(schema, TGT, "x"), none);
                    case NO_USABLE_SEARCH ->
                        Map.of(
                                PermissionTarget.column(schema, TGT, "s"),
                                none,
                                PermissionTarget.column(schema, TGT, "n"),
                                none);
                    default -> Map.of();
                };
        return tables.apiSettings(overrides);
    }

    private HttpResponse<String> send(Mode mode, Subject subject, String token) {
        String column =
                switch (subject) {
                    case BINARY_VALUE -> "pickbin";
                    case PASSWORD_VALUE -> "pickpw";
                    default -> "pick";
                };
        String searchColumn = column.equals("pick") ? "s" : "l";
        return mode == Mode.SEARCH
                ? app.post(
                        BusinessDataPaths.REFERENCE_ROWS,
                        token,
                        sample.body(
                                SRC_API,
                                "column",
                                column,
                                "conditions",
                                List.of(Map.of("column", searchColumn, "value", "S1")),
                                "page",
                                1))
                : app.post(
                        BusinessDataPaths.REFERENCE_ROWS, token, sample.body(SRC_API, "column", column, "value", "V1"));
    }

    @ParameterizedTest(name = "{0} by {1} gives {2}")
    @MethodSource("table")
    @DisplayName(
            "the API is 401 without login or when suspended, 403 when exactly one permission is missing, 200 with all")
    void authorization(Mode mode, Subject subject, int status, String code) {
        Actor member = app.reader(settings(subject));
        if (subject == Subject.SUSPENDED) {
            app.roleActors().suspend(member.userId());
        }
        String token = subject == Subject.ANONYMOUS ? null : member.token();

        HttpResponse<String> response = send(mode, subject, token);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        if (code != null) {
            assertThat(HttpTestClient.json(response)).containsEntry("code", code);
        }
        if (status == 403) {
            assertThat(HttpTestClient.json(response)).doesNotContainKey("fieldErrors");
            assertThat(response.body()).doesNotContain(TGT + "\"").doesNotContain("L1");
        }
        if (status == 200) {
            assertThat(response.body()).contains("\"label\":\"L1\"");
        }
    }

    @Test
    @DisplayName("switching the work role and changing the assignment switch 403 and 200 on the very next request")
    void switchesOnTheNextRequest() {
        long fullRole = app.role(settings(Subject.FULL));
        long noneRole = app.role(settings(Subject.TARGET_TABLE_NONE));
        Actor member = app.reader(settings(Subject.TARGET_TABLE_NONE));
        app.fixtures().assignUser(fullRole, member.userId());
        app.fixtures().assignUser(noneRole, member.userId());

        app.fixtures().selectWorkRole(member.userId(), fullRole);
        assertThat(send(Mode.SEARCH, Subject.FULL, member.token()).statusCode()).isEqualTo(200);
        app.fixtures().selectWorkRole(member.userId(), noneRole);
        assertThat(send(Mode.SEARCH, Subject.FULL, member.token()).statusCode()).isEqualTo(403);
        app.fixtures().selectWorkRole(member.userId(), fullRole);
        assertThat(send(Mode.VALUE, Subject.FULL, member.token()).statusCode()).isEqualTo(200);

        JdbcTemplate jdbc = app.bean(JdbcTemplate.class);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ? AND role_id = ?", member.userId(), fullRole);
        assertThat(send(Mode.VALUE, Subject.FULL, member.token()).statusCode())
                .as("割り当てを外した直後")
                .isEqualTo(403);
        app.fixtures().assignUser(fullRole, member.userId());
        app.fixtures().selectWorkRole(member.userId(), fullRole);
        assertThat(send(Mode.SEARCH, Subject.FULL, member.token()).statusCode())
                .as("割り当てた直後")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("the definition reports NO_PERMISSION in a 200 for a user who lacks the target (AC1.3.1)")
    @SuppressWarnings("unchecked")
    void definitionStaysAt200() {
        Actor member = app.reader(settings(Subject.TARGET_TABLE_NONE));

        HttpResponse<String> response =
                app.post(BusinessDataPaths.DEFINITION, member.token(), sample.body(SRC_API, "purpose", "CREATE"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<Map<String, Object>> columns =
                (List<Map<String, Object>>) HttpTestClient.json(response).get("detailColumns");
        assertThat(columns)
                .filteredOn(c -> List.of("pick", "ref").contains(c.get("column")))
                .allSatisfy(c ->
                        assertThat((Map<String, Object>) c.get("reference")).containsEntry("status", "NO_PERMISSION"));
    }
}
