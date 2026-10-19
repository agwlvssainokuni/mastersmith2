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

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
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
 * 業務データの読み取りの API 3本の認可の表（BR1.4・BR11.1、AC1.1.5・AC3.1.4・AC3.1.5・AC3.5.1・AC3.5.2、team.md の「権限ごとの操作の
 * 可否」）。PostgreSQL のコンテナ。403 を確かめる利用者は、要るテーブルだけ NONE でほかのテーブルは READ の作業ロールを持つ。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataAuthorizationApiIT {

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target());
        app.installDsl(sample.model());
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

    /** API。 */
    enum Endpoint {
        DEFINITION,
        SEARCH,
        GET
    }

    /** 主体。 */
    enum Subject {
        ANONYMOUS,
        SUSPENDED,
        READ,
        FULL,
        ONLY_THIS_NONE
    }

    static Stream<Arguments> table() {
        Stream.Builder<Arguments> rows = Stream.builder();
        for (Endpoint endpoint : Endpoint.values()) {
            rows.add(Arguments.of(endpoint, Subject.ANONYMOUS, 401, "AUTHENTICATION_REQUIRED"));
            rows.add(Arguments.of(endpoint, Subject.SUSPENDED, 401, "AUTHENTICATION_REQUIRED"));
            rows.add(Arguments.of(endpoint, Subject.READ, 200, null));
            rows.add(Arguments.of(endpoint, Subject.FULL, 200, null));
            rows.add(Arguments.of(endpoint, Subject.ONLY_THIS_NONE, 403, "ACCESS_DENIED"));
        }
        return rows.build();
    }

    @ParameterizedTest(name = "{0} by {1} gives {2}")
    @MethodSource("table")
    @DisplayName("each API is 401 without login or when suspended, 403 without the table and 200 with it")
    void authorization(Endpoint endpoint, Subject subject, int status, String code) {
        MainPermission item =
                switch (subject) {
                    case FULL -> MainPermission.FULL;
                    case ONLY_THIS_NONE -> MainPermission.NONE;
                    default -> MainPermission.READ;
                };
        Actor member = app.reader(sample.settings(item));
        if (subject == Subject.SUSPENDED) {
            app.roleActors().suspend(member.userId());
        }
        String token = subject == Subject.ANONYMOUS ? null : member.token();

        HttpResponse<String> response = send(endpoint, token);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        if (code != null) {
            assertThat(HttpTestClient.json(response)).containsEntry("code", code);
        }
        if (status == 403 && endpoint == Endpoint.GET) {
            assertThat(response.body()).doesNotContain(BusinessDataSampleSchema.SECRET_NAME);
        }
    }

    @Test
    @DisplayName("switching the work role and changing the assignment switch 403 and 200 on the very next request")
    void switchesOnTheNextRequest() {
        long readRole = app.role(sample.settings(MainPermission.READ));
        long noneRole = app.role(sample.settings(MainPermission.NONE));
        Actor member = app.reader(sample.settings(MainPermission.NONE));
        app.fixtures().assignUser(readRole, member.userId());
        app.fixtures().assignUser(noneRole, member.userId());

        app.fixtures().selectWorkRole(member.userId(), readRole);
        assertThat(send(Endpoint.SEARCH, member.token()).statusCode()).isEqualTo(200);
        app.fixtures().selectWorkRole(member.userId(), noneRole);
        assertThat(send(Endpoint.SEARCH, member.token()).statusCode()).isEqualTo(403);
        app.fixtures().selectWorkRole(member.userId(), readRole);
        assertThat(send(Endpoint.GET, member.token()).statusCode()).isEqualTo(200);

        JdbcTemplate jdbc = app.bean(JdbcTemplate.class);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ? AND role_id = ?", member.userId(), readRole);
        assertThat(send(Endpoint.GET, member.token()).statusCode())
                .as("割り当てを外した直後")
                .isEqualTo(403);
        app.fixtures().assignUser(readRole, member.userId());
        app.fixtures().selectWorkRole(member.userId(), readRole);
        assertThat(send(Endpoint.DEFINITION, member.token()).statusCode())
                .as("割り当てた直後")
                .isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("an authorized search returns the rows and an authorized detail returns the row")
    void authorizedContent() {
        Actor member = app.reader(sample.settings(MainPermission.READ));

        Map<String, Object> page = HttpTestClient.json(send(Endpoint.SEARCH, member.token()));
        Map<String, Object> detail = HttpTestClient.json(send(Endpoint.GET, member.token()));

        assertThat(page).containsEntry("total", 3);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) detail.get("values");
        assertThat(values)
                .containsOnlyKeys("id", "name", "price", "tag")
                .containsEntry("id", "1")
                .containsEntry("name", BusinessDataSampleSchema.SECRET_NAME)
                .containsEntry("price", "100.00")
                .containsEntry("tag", null);
    }

    private HttpResponse<String> send(Endpoint endpoint, String token) {
        String table = BusinessDataSampleSchema.ITEM;
        return switch (endpoint) {
            case DEFINITION -> app.post(BusinessDataPaths.DEFINITION, token, sample.body(table));
            case SEARCH -> app.post(BusinessDataPaths.SEARCH, token, sample.body(table, "page", 1, "size", 20));
            case GET -> app.post(BusinessDataPaths.GET, token, sample.body(table, "key", Map.of("id", "1")));
        };
    }
}
