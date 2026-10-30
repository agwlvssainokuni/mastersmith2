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
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_TGT;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 保存前の参照の確かめの認可の結合テスト（Intent 261029-reference-lookup-cleanup の U2、計画の Step 10、(a)(g) の書き込み側、
 * AC3.1.3・AC3.1.4・AC3.2.3）。PostgreSQL のコンテナ。参照元は FULL で、参照先の要る権限だけを欠く利用者の作成・更新が 409 で状態が
 * 変わらないこと、作業ロール・割り当てを変えた直後の次の要求で結果が切り替わること、既存の 401・403 が ⑩ より先であることを確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
@SuppressWarnings("unchecked")
class ReferenceWriteAuthorizationApiIT {

    /** 参照先について欠く権限。 */
    enum Lack {
        /** 参照先の表が NONE。 */
        TARGET_TABLE_NONE,
        /** 参照先の値のカラムが NONE。 */
        VALUE_COLUMN_NONE
    }

    private final AtomicInteger ids = new AtomicInteger(2_000);

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private ReferenceTables tables;

    private BusinessDataTestApp app;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        tables = new ReferenceTables(sample);
        tables.createWrite();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[0]);
        app.installDsl(sample.model(tables.writeTables()));
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

    private Map<PermissionTarget, PermissionValues> settings(Lack lack) {
        Map<PermissionTarget, PermissionValues> overrides = new LinkedHashMap<>();
        if (lack == Lack.TARGET_TABLE_NONE) {
            overrides.put(
                    PermissionTarget.table(sample.schema(), W_TGT),
                    new PermissionValues(MainPermission.NONE, null, null));
        } else if (lack == Lack.VALUE_COLUMN_NONE) {
            overrides.put(
                    PermissionTarget.column(sample.schema(), W_TGT, "v"),
                    new PermissionValues(MainPermission.NONE, null, null));
        }
        return tables.writeSettings(overrides);
    }

    private HttpResponse<String> create(Actor actor, Map<String, String> values) {
        Map<String, Object> body = sample.body(WS_MAIN);
        if (actor != null) {
            body.put("definitionId", app.definitionId(actor.token(), sample.schema(), WS_MAIN));
        } else {
            body.put("definitionId", Map.of("dslHash", "x", "workRoleId", 1));
        }
        body.put("values", values);
        return app.post(BusinessDataPaths.ROWS, actor == null ? null : actor.token(), body);
    }

    private HttpResponse<String> update(Actor actor, int id, Map<String, String> values) {
        Map<String, Object> body = sample.body(WS_MAIN);
        body.put("definitionId", app.definitionId(actor.token(), sample.schema(), WS_MAIN));
        body.put("key", Map.of("id", Integer.toString(id)));
        body.put("values", values);
        return app.patch(BusinessDataPaths.ROWS, actor.token(), body);
    }

    private static void rejected(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        Map<String, Object> json = HttpTestClient.json(response);
        assertThat(json).containsEntry("code", "BUSINESS_REFERENCE_NOT_FOUND");
        assertThat((List<Map<String, Object>>) json.get("fieldErrors"))
                .containsExactly(Map.of("field", "ref", "reason", "REFERENCE_NOT_FOUND"));
    }

    private Map<String, String> row(int id) {
        return BusinessDataWriteTables.query(
                        sample.database(),
                        "SELECT * FROM " + sample.database().q(sample.schema()) + "." + WS_MAIN + " WHERE id = " + id)
                .getFirst();
    }

    private int insertBase(String ref) {
        int id = ids.incrementAndGet();
        sample.addObjects("INSERT INTO %s" + WS_MAIN + " (id, name, ref) VALUES (" + id + ", '前', '" + ref + "')");
        return id;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Lack.class)
    @DisplayName("a user who lacks only the target permission gets 409 for create and changed update, nothing changes")
    void lackingTargetPermissionIs409(Lack lack) {
        Actor actor = app.reader(settings(lack));
        int id = ids.incrementAndGet();

        rejected(create(actor, Map.of("id", Integer.toString(id), "ref", "V1")));
        assertThat(sample.count(WS_MAIN, "id = " + id)).isZero();

        int base = insertBase("V2");
        rejected(update(actor, base, Map.of("ref", "V1", "name", "後")));
        assertThat(row(base)).containsEntry("ref", "V2").containsEntry("name", "前");

        HttpResponse<String> unchanged = update(actor, base, Map.of("ref", "V2", "name", "後"));
        assertThat(unchanged.statusCode()).as("値の変わらない参照は確かめない（AC3.2.3）").isEqualTo(200);
    }

    @Test
    @DisplayName("a user with the target permission saves the same request")
    void permittedUserSaves() {
        Actor actor = app.reader(tables.writeSettings(Map.of()));
        int id = ids.incrementAndGet();

        HttpResponse<String> response = create(actor, Map.of("id", Integer.toString(id), "ref", "V1"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    @Test
    @DisplayName("a target removed from the DSL is answered like an unreadable one")
    void targetRemovedFromTheDsl() {
        Actor actor = app.reader(tables.writeSettings(Map.of()));
        DslModel full = sample.model(tables.writeTables());
        DslSchema schema = full.schemas().getFirst();
        Map<String, DslTable> withoutTarget = new LinkedHashMap<>(schema.tables());
        withoutTarget.remove(W_TGT);
        app.installDsl(new DslModel(
                full.dslHash(),
                full.formatVersion(),
                List.of(new DslSchema(schema.name(), schema.label(), withoutTarget)),
                full.menus()));
        try {
            rejected(create(actor, Map.of("id", Integer.toString(ids.incrementAndGet()), "ref", "V1")));
        } finally {
            app.installDsl(full);
        }
    }

    @Test
    @DisplayName("switching the work role or the assignment changes the answer on the very next request")
    void switchesOnTheNextRequest() {
        long fullRole = app.role(tables.writeSettings(Map.of()));
        long noneRole = app.role(settings(Lack.TARGET_TABLE_NONE));
        Actor member = app.reader(settings(Lack.TARGET_TABLE_NONE));
        app.fixtures().assignUser(fullRole, member.userId());
        app.fixtures().assignUser(noneRole, member.userId());

        app.fixtures().selectWorkRole(member.userId(), fullRole);
        assertThat(create(member, Map.of("id", Integer.toString(ids.incrementAndGet()), "ref", "V1"))
                        .statusCode())
                .isEqualTo(201);
        app.fixtures().selectWorkRole(member.userId(), noneRole);
        rejected(create(member, Map.of("id", Integer.toString(ids.incrementAndGet()), "ref", "V1")));
        app.fixtures().selectWorkRole(member.userId(), fullRole);
        JdbcTemplate jdbc = app.bean(JdbcTemplate.class);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ? AND role_id = ?", member.userId(), fullRole);
        app.fixtures().selectWorkRole(member.userId(), noneRole);
        rejected(create(member, Map.of("id", Integer.toString(ids.incrementAndGet()), "ref", "V1")));
    }

    @Test
    @DisplayName("401 without a token and 403 without the CREATE permission come before the reference step")
    void existingOrderIsKept() {
        assertThat(create(null, Map.of("id", "1", "ref", "V9")).statusCode()).isEqualTo(401);
        Map<PermissionTarget, PermissionValues> noCreate = new LinkedHashMap<>();
        noCreate.put(PermissionTarget.schema(sample.schema()), new PermissionValues(MainPermission.FULL, false, false));
        Actor actor = app.reader(noCreate);

        HttpResponse<String> response = create(actor, Map.of("id", "1", "ref", "V9"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }
}
