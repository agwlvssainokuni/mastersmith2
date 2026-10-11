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
package cherry.mastersmith.role.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 書き出しの大きさと読み込みの上限（NFR2.9、計画の D-21）の結合テスト。上限をテストだけで小さくし
 * （{@code mastersmith.role-transfer.max-bytes}）、上限ちょうどの書き出しにはヘッダーが無く、1 バイト超えには
 * {@code X-Role-Transfer-Exceeds-Import-Limit: true} が付くこと、超えたファイルを2つに分けてそれぞれ確かめと適用ができることを確かめる。
 * 1つの内部DB の中のロールの数で書き出しの大きさが決まるため、1つのテストの中で順に確かめる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "mastersmith.auth.password.bcrypt-cost=4",
            "mastersmith.role-transfer.max-bytes=" + RoleTransferExportLimitIT.LIMIT
        })
@Import(AuthApiTestConfig.class)
class RoleTransferExportLimitIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    RefreshTokenRevocationService revocationService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    JdbcTemplate jdbc;

    private RoleApi api;

    private RoleFixtures fixtures;

    private Actor admin;

    @BeforeEach
    void setUp() {
        api = new RoleApi(port);
        fixtures = new RoleFixtures(jdbc);
        admin = new RoleActors(userAccountService, revocationService, transactionManager, port).admin();
        RoleDslFixture.install(holder, RoleDslFixture.sample());
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response)).containsEntry("code", code);
    }

    static final int LIMIT = 2_000;

    private int sequence;

    private int exportLength() {
        HttpResponse<String> response = api.export(admin.token());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body().getBytes(StandardCharsets.UTF_8).length;
    }

    /** 書き出しの1行（{@code "  <名前>: {}\n"}）の長さが length になる名前のロールを足す（名前は英小文字だけで引用符が要らない）。 */
    private void addRoleLine(int length) {
        int nameLength = length - 7;
        String prefix = "r" + (char) ('a' + sequence / 26) + (char) ('a' + sequence % 26);
        sequence++;
        fixtures.role(prefix + "x".repeat(nameLength - prefix.length()));
    }

    @Test
    @DisplayName(
            "an export of exactly the limit has no header, one byte more has it, and the halves can be checked and applied")
    void exportAndSplit() {
        addRoleLine(71);
        int remaining = LIMIT - exportLength();
        while (remaining > 71 + 10) {
            addRoleLine(71);
            remaining -= 71;
        }
        if (remaining > 71) {
            addRoleLine(remaining - 10);
            remaining = 10;
        }
        addRoleLine(remaining);

        HttpResponse<String> exact = api.export(admin.token());
        assertThat(exact.body().getBytes(StandardCharsets.UTF_8)).hasSize(LIMIT);
        assertThat(exact.headers().firstValue("X-Role-Transfer-Exceeds-Import-Limit"))
                .isEmpty();

        jdbc.update("UPDATE roles SET name = CONCAT(name, 'y'), name_key = CONCAT(name_key, 'y')"
                + " WHERE role_id = (SELECT MAX(role_id) FROM roles)");
        HttpResponse<String> over = api.export(admin.token());
        assertThat(over.body().getBytes(StandardCharsets.UTF_8)).hasSize(LIMIT + 1);
        assertThat(over.headers().firstValue("X-Role-Transfer-Exceeds-Import-Limit"))
                .hasValue("true");
        assertCode(api.check(admin.token(), over.body()), 413, "PAYLOAD_TOO_LARGE");

        List<String> lines = new ArrayList<>(List.of(over.body().split("\n")));
        List<String> roleLines = lines.subList(2, lines.size());
        int half = roleLines.size() / 2;
        for (List<String> part : List.of(roleLines.subList(0, half), roleLines.subList(half, roleLines.size()))) {
            String yaml = "version: 1\nroles:\n"
                    + String.join(
                            "\n",
                            part.stream()
                                    .map(line -> line.replaceFirst("^  r", "  n"))
                                    .toList()) + "\n";
            assertThat(yaml.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(LIMIT);

            HttpResponse<String> checked = api.check(admin.token(), yaml);
            assertThat(checked.statusCode()).as(checked.body()).isEqualTo(200);
            HttpResponse<String> applied = api.apply(admin.token(), yaml, RoleApi.fingerprintOf(checked));

            assertThat(applied.statusCode()).as(applied.body()).isEqualTo(200);
            assertThat(json(applied)).containsEntry("created", part.size()).containsEntry("replaced", 0);
        }
    }
}
