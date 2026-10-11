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
import cherry.mastersmith.dsl.service.SafeYamlLimits;
import cherry.mastersmith.role.service.RoleTransferLimits;
import cherry.mastersmith.role.testsupport.LargeRoleTransferYaml;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
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
 * 権限の YAML の信頼できない入力の守り（NFR1.5、BR9.3・BR9.4、AC3.1.7・AC3.1.12・AC3.1.13、試し U1・U2、計画の D-38）の結合テスト。
 * 大きさ（10,485,760 バイト）・深さ（10）・コレクションの別名（0）の境界、展開後の節の上限を超えた拒否、タグ、重複キー、知らない
 * 項目、版の誤りを、確かめの口に送って確かめる。展開後の節の上限ちょうど（1,000,000）の受け付けは、テストの JVM のヒープを Spring の
 * アプリと分け合わないよう、{@code RoleTransferReaderTest} で確かめる（依頼者の決定 A）。拒否は Problem Details の {@code ROLE_TRANSFER_INVALID}（400）か {@code PAYLOAD_TOO_LARGE}（413）で、
 * 読み込みの部品の例外の文を含まない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleTransferLimitsApiIT {

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
    RoleTransferLimits limits;

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

    private static final int LIMIT = 10_485_760;

    @SuppressWarnings("unchecked")
    private static List<String> reasons(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(json(response)).containsEntry("code", "ROLE_TRANSFER_INVALID");
        assertThat(response.body())
                .as("部品の例外の文を含まない")
                .doesNotContain("Exception")
                .doesNotContain("snakeyaml")
                .doesNotContainIgnoringCase("found duplicate");
        List<Map<String, Object>> errors =
                (List<Map<String, Object>>) json(response).get("errors");
        assertThat(errors).allSatisfy(error -> assertThat(error).containsOnlyKeys("reason", "line", "column", "path"));
        return errors.stream().map(error -> (String) error.get("reason")).toList();
    }

    /** 入れ子の深さが depth の YAML（根の対応表を 1 段目とし、各段が1つの対応表）。 */
    private static String nested(int depth) {
        StringBuilder yaml = new StringBuilder("version: 1\nroles:\n");
        for (int level = 3; level <= depth; level++) {
            yaml.append("  ".repeat(level - 2)).append("k").append(level).append(":\n");
        }
        yaml.append("  ".repeat(depth - 1)).append("leaf: 1\n");
        return yaml.toString();
    }

    @Test
    @DisplayName("exactly 10,485,760 bytes is accepted and one byte more is 413 with Content-Length and with chunks")
    void size() {
        byte[] exact = LargeRoleTransferYaml.generateBytes(LargeRoleTransferYaml.Spec.of(20, 10_000, 30, LIMIT));
        byte[] over = LargeRoleTransferYaml.generateBytes(LargeRoleTransferYaml.Spec.of(20, 10_000, 30, LIMIT + 1));

        HttpResponse<String> accepted =
                api.sendYaml("/api/admin/role-transfer/check", admin.token(), exact, "application/yaml");
        HttpResponse<String> tooLarge =
                api.sendYaml("/api/admin/role-transfer/check", admin.token(), over, "application/yaml");
        HttpResponse<String> chunked = new HttpTestClient(port)
                .send(new HttpTestClient(port)
                        .request("/api/admin/role-transfer/check")
                        .header("Authorization", "Bearer " + admin.token())
                        .header("Content-Type", "application/yaml")
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(over)))
                        .build());

        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        assertCode(tooLarge, 413, "PAYLOAD_TOO_LARGE");
        assertCode(chunked, 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("nesting depth 10 is read and 11 is TOO_DEEP")
    void depth() {
        assertThat(reasons(api.check(admin.token(), nested(10)))).doesNotContain("TOO_DEEP");
        assertThat(reasons(api.check(admin.token(), nested(11)))).containsExactly("TOO_DEEP");
    }

    @Test
    @DisplayName("no collection alias is accepted, one is TOO_MANY_ALIASES, and an alias to a plain value is read (U2)")
    void aliases() {
        String noAlias = "version: 1\nroles:\n  r:\n    schemas:\n      SALES: {main: READ}\n      HR: {main: READ}\n";
        String collectionAlias = "version: 1\nroles:\n  r:\n    schemas:\n      SALES: &s {main: READ}\n      HR: *s\n";
        String scalarAlias =
                "version: 1\nroles:\n  r:\n    schemas:\n      SALES: {main: &m READ}\n      HR: {main: *m}\n";
        String explosion =
                "version: 1\nroles:\n  a: &a [x, x, x, x, x, x, x, x, x, x]\n  b: &b [*a, *a, *a, *a, *a, *a, *a]\n"
                        + "  c: [*b, *b, *b, *b, *b, *b, *b, *b]\n";

        assertThat(api.check(admin.token(), noAlias).statusCode()).isEqualTo(200);
        assertThat(reasons(api.check(admin.token(), collectionAlias))).containsExactly("TOO_MANY_ALIASES");
        assertThat(api.check(admin.token(), scalarAlias).statusCode()).isEqualTo(200);
        assertThat(reasons(api.check(admin.token(), explosion))).containsExactly("TOO_MANY_ALIASES");
    }

    @Test
    @DisplayName("the configured limits give the safe reader 1,000,000 expanded nodes (the boundary is a unit test)")
    void expandedNodeLimit() {
        assertThat(limits.toSafeYamlLimits()).isEqualTo(new SafeYamlLimits(LIMIT, 10, 0, 1_000_000));
    }

    @Test
    @DisplayName("tags, duplicate role and target keys, unknown keys and a wrong version are refused with positions")
    void contentRefusals() {
        assertThat(reasons(api.check(admin.token(), "version: 1\nroles: !!map {}\n")))
                .containsExactly("TAG_NOT_ALLOWED");
        assertThat(reasons(api.check(admin.token(), "version: 1\nroles:\n  r: {}\n  r: {}\n")))
                .containsExactly("DUPLICATE_KEY");
        assertThat(reasons(api.check(
                        admin.token(),
                        "version: 1\nroles:\n  r:\n    schemas:\n      1: {main: READ}\n      '1': {}\n")))
                .containsExactly("DUPLICATE_KEY");
        assertThat(reasons(api.check(admin.token(), "version: 1\nroles:\n  Sales: {}\n  sales: {}\n")))
                .containsExactly("DUPLICATE_ROLE");
        assertThat(reasons(api.check(admin.token(), "version: 1\nroles:\n  r: {owner: x}\n")))
                .containsExactly("UNKNOWN_KEY");
        assertThat(reasons(api.check(admin.token(), "version: 9\nroles: {}\n"))).containsExactly("UNSUPPORTED_VERSION");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = ((List<Map<String, Object>>) json(api.check(
                                admin.token(), "version: 1\nroles:\n  r:\n    schemas:\n      S: {main: read}\n"))
                        .get("errors"))
                .getFirst();
        assertThat(error)
                .containsEntry("reason", "INVALID_VALUE")
                .containsEntry("line", 5)
                .containsEntry("path", "roles.r.schemas.S.main");
    }

    @Test
    @DisplayName("the apply is guarded by the same limits before the fingerprint is compared")
    void applyGuarded() {
        HttpResponse<String> tagged = api.apply(admin.token(), "version: 1\nroles: !!map {}\n", "a".repeat(64));

        assertThat(reasons(tagged)).containsExactly("TAG_NOT_ALLOWED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED'", Integer.class))
                .isZero();
    }
}
