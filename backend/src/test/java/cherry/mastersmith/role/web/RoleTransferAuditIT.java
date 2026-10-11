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
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleApi;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.role.transfer.TransferFingerprint;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * 権限の YAML の適用の監査（BR11.4・BR11.5・BR11.7・BR11.3、NFR5.1・NFR5.5・NFR5.6、AC1.1.16・AC3.1.9）の結合テスト。適用は1回を1行にし、
 * 対象の列を空にして detail にファイルの SHA-256・指紋・ロールごとの要約を書く。確かめと書き出しは行を増やさない。ロールが多く detail が
 * 上限を超えるときは要約に切り替わる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class RoleTransferAuditIT {

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

    private List<Map<String, Object>> transferRows() {
        return jdbc.queryForList(
                "SELECT * FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED' ORDER BY audit_event_id");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detail(Map<String, Object> row) {
        return JsonMapper.shared().readValue((String) row.get("DETAIL"), Map.class);
    }

    @Test
    @DisplayName(
            "one apply is one row with the actor, empty targets and the file hash, fingerprint and per role counts")
    void applyRow() {
        String name = RoleFixtures.uniqueName("監査");
        long roleId = fixtures.role(name);
        fixtures.setting(roleId, PermissionTarget.schema("HR"), MainPermission.READ);
        String yaml = "version: 1\nroles:\n  \"" + name
                + "\":\n    schemas:\n      SALES: {main: READ}\n      GONE: {main: FULL}\n";
        int before = transferRows().size();

        HttpResponse<String> checked = api.check(admin.token(), yaml);
        api.export(admin.token());
        assertThat(transferRows()).as("確かめと書き出しは監査に残さない").hasSize(before);
        String fingerprint = RoleApi.fingerprintOf(checked);
        assertThat(api.apply(admin.token(), yaml, fingerprint).statusCode()).isEqualTo(200);

        List<Map<String, Object>> rows = transferRows();
        assertThat(rows).hasSize(before + 1);
        Map<String, Object> row = rows.getLast();
        assertThat(row.get("RESULT")).isEqualTo("SUCCESS");
        assertThat(((Number) row.get("ACTOR_USER_ID")).longValue()).isEqualTo(admin.userId());
        assertThat(row.get("TARGET_ROLE_ID")).isNull();
        assertThat(row.get("TARGET_USER_ID")).isNull();
        assertThat(row.get("TARGET_GROUP_ID")).isNull();
        assertThat(row.get("TRACE_ID")).isNotNull();
        Map<String, Object> detail = detail(row);
        assertThat(detail)
                .containsEntry("fileSha256", TransferFingerprint.sha256Hex(yaml.getBytes(StandardCharsets.UTF_8)))
                .containsEntry("fingerprint", fingerprint);
        assertThat(detail.get("roles"))
                .isEqualTo(List.of(Map.of(
                        "name", name,
                        "roleId", (int) roleId,
                        "action", "REPLACE",
                        "added", 2,
                        "changed", 0,
                        "cleared", 1,
                        "notInDsl", 1)));
    }

    @Test
    @DisplayName("a stale apply is one FAILURE row with the requested fingerprint and an empty target")
    void staleRow() {
        String name = RoleFixtures.uniqueName("監査 古い");
        String yaml = "version: 1\nroles:\n  \"" + name + "\":\n    schemas:\n      SALES: {main: READ}\n";
        String requested = "e".repeat(64);

        assertThat(api.apply(admin.token(), yaml, requested).statusCode()).isEqualTo(409);

        Map<String, Object> row = transferRows().getLast();
        assertThat(row.get("RESULT")).isEqualTo("FAILURE");
        assertThat(row.get("FAILURE_REASON")).isEqualTo("ROLE_TRANSFER_STALE");
        assertThat(row.get("TARGET_ROLE_ID")).isNull();
        assertThat(detail(row)).containsEntry("fingerprint", requested);
    }

    @Test
    @DisplayName("a detail over 16,384 characters is a summary of the role count and at most 50 first roles")
    void summary() {
        String yaml = "version: 1\nroles:\n"
                + IntStream.range(0, 300)
                        .mapToObj(i -> "  \"" + RoleFixtures.uniqueName("監査 要約 " + "x".repeat(20)) + "\": {}\n")
                        .collect(Collectors.joining());
        String fingerprint = RoleApi.fingerprintOf(api.check(admin.token(), yaml));

        assertThat(api.apply(admin.token(), yaml, fingerprint).statusCode()).isEqualTo(200);

        Map<String, Object> row = transferRows().getLast();
        assertThat(((String) row.get("DETAIL")).length()).isLessThanOrEqualTo(16_384);
        Map<String, Object> detail = detail(row);
        assertThat(detail).containsEntry("roleCount", 300).doesNotContainKey("roles");
        assertThat((List<?>) detail.get("firstRoles")).hasSize(50);
    }
}
