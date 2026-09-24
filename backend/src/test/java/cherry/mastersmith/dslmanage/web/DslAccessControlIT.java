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

import cherry.mastersmith.access.testsupport.AdminAccessTestConfig;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.audit.testsupport.AuditRows;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.dslmanage.testsupport.DslApi;
import cherry.mastersmith.dslmanage.testsupport.DslAuditRows;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DSL の管理の API のアクセス制御の一括の確かめ（Intent 260923-dsl-schema-loader の U5、BR8.1・BR8.1a・BR8.2、
 * AC6.2.1〜AC6.2.3）。契約 C6 の 10 本の API を1つの一覧にし、未認証 401・管理者でない 403（アクセス拒否の監査の出来事つき）・
 * 管理者の成功を確かめ、401・403 では内部DB のプレビューと履歴が変わらないことを確かめる。各 API の個別の確かめは U4 が持つ。
 *
 * <p>組み込みの H2 で起動し、対象DB は設定しない。そのためスキーマの読み込みの管理者の結果は、401・403 ではなく、設定が無いときの
 * 503 {@code TARGET_DB_UNCONFIGURED} で確かめる。画面でサイドバーの項目を隠すことは、この確かめの代わりにしない。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AdminAccessTestConfig.class)
class DslAccessControlIT {

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
    JdbcTemplate jdbc;

    @Autowired
    ActiveDslModelHolder activeDslModelHolder;

    private AdminTestUsers users;

    private DslApi admin;

    private DslApi member;

    private DslApi anonymous;

    private Fixture fixture;

    /** 事前に用意したデータ（適用した版が1つと、それとは別のプレビューが1つ）。 */
    private record Fixture(String previewId, String revisionId) {}

    /**
     * 一覧の1本の API。
     *
     * @param name 名前（テストの表示）
     * @param path 監査に記録される要求のパス（問い合わせの部分を除く）
     * @param call 呼び出し
     * @param adminStatus 管理者の成功の状態コード
     * @param adminCode 管理者の結果の code（成功なら null）
     */
    private record Endpoint(
            String name,
            Function<Fixture, String> path,
            BiFunction<DslApi, Fixture, HttpResponse<String>> call,
            int adminStatus,
            String adminCode) {

        @Override
        public String toString() {
            return name;
        }
    }

    private static byte[] sample(String table) {
        return DslYaml.dsl()
                .menu("表", "Table", table)
                .table(table, column("code"), column("name"))
                .bytes();
    }

    /** 契約 C6 の 10 本の API の一覧。 */
    static Stream<Endpoint> endpoints() {
        String root = DslApi.ROOT;
        return Stream.of(
                new Endpoint("GET /status", f -> root + "/status", (api, f) -> api.get("/status"), 200, null),
                new Endpoint("GET /preview", f -> root + "/preview", (api, f) -> api.get("/preview"), 200, null),
                new Endpoint(
                        "POST /preview",
                        f -> root + "/preview",
                        (api, f) -> api.submit(sample("submitted"), "PASTE"),
                        201,
                        null),
                new Endpoint("DELETE /preview", f -> root + "/preview", (api, f) -> api.discard(), 204, null),
                new Endpoint(
                        "POST /preview/generate",
                        f -> root + "/preview/generate",
                        (api, f) -> api.generate(),
                        503,
                        "TARGET_DB_UNCONFIGURED"),
                new Endpoint(
                        "GET /preview/download",
                        f -> root + "/preview/download",
                        (api, f) -> api.get("/preview/download"),
                        200,
                        null),
                new Endpoint("POST /apply", f -> root + "/apply", (api, f) -> api.apply(f.previewId()), 200, null),
                new Endpoint(
                        "GET /applied/download",
                        f -> root + "/applied/download",
                        (api, f) -> api.get("/applied/download"),
                        200,
                        null),
                new Endpoint("GET /history", f -> root + "/history", (api, f) -> api.get("/history"), 200, null),
                new Endpoint(
                        "POST /history/{revisionId}/restore",
                        f -> root + "/history/" + f.revisionId() + "/restore",
                        (api, f) -> api.restore(f.revisionId()),
                        201,
                        null));
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM dsl_previews");
        jdbc.update("DELETE FROM dsl_applied_revisions");
        activeDslModelHolder.replace(null);
        users = new AdminTestUsers(userAccountService, port);
        admin = new DslApi(port, users.accessToken(users.createAdmin()));
        member = new DslApi(port, users.accessToken(users.createNonAdmin()));
        anonymous = new DslApi(port, null);

        HttpResponse<String> applied = admin.apply(admin.submitOk(sample("applied")));
        assertThat(applied.statusCode()).isEqualTo(200);
        String revisionId =
                (String) DslApi.jsonList(admin.get("/history")).getFirst().get("revisionId");
        String previewId = admin.submitOk(sample("previewed"));
        fixture = new Fixture(previewId, revisionId);
    }

    /** 内部DB のプレビューと履歴の中身（比べるための写し）。 */
    private List<String> dslState() {
        List<String> previews = jdbc.query(
                "SELECT preview_id, dsl_hash, source, placed_by_user_id, placed_at FROM dsl_previews",
                (rs, rowNum) -> "preview|" + rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                        + rs.getLong(4) + "|" + rs.getString(5));
        List<String> revisions = jdbc.query(
                "SELECT revision_id, dsl_hash, source, applied_by_user_id, applied_at FROM dsl_applied_revisions"
                        + " ORDER BY sequence_no",
                (rs, rowNum) -> "revision|" + rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                        + rs.getLong(4) + "|" + rs.getString(5));
        return Stream.concat(previews.stream(), revisions.stream()).toList();
    }

    private List<AuditRows.AuditRow> notAdminDenials(String path) {
        return new AuditRows(jdbc)
                .all().stream()
                        .filter(row -> "ACCESS_DENIED".equals(row.eventType()))
                        .filter(row -> "NOT_ADMIN".equals(row.failureReason()))
                        .filter(row -> path.equals(row.requestPath()))
                        .toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("an anonymous request gets 401 and changes neither the preview nor the history")
    void anonymousIsRejected(Endpoint endpoint) {
        List<String> before = dslState();
        int dslAuditRows = new DslAuditRows(jdbc).dslRows().size();

        HttpResponse<String> response = endpoint.call().apply(anonymous, fixture);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(dslState()).isEqualTo(before);
        assertThat(new DslAuditRows(jdbc).dslRows()).hasSize(dslAuditRows);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("a user without the administrator flag gets 403, is audited, and changes nothing")
    void memberIsRejectedAndAudited(Endpoint endpoint) {
        List<String> before = dslState();
        int dslAuditRows = new DslAuditRows(jdbc).dslRows().size();
        String path = endpoint.path().apply(fixture);
        int denials = notAdminDenials(path).size();

        HttpResponse<String> response = endpoint.call().apply(member, fixture);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(DslApi.json(response)).containsEntry("code", "ACCESS_DENIED");
        assertThat(notAdminDenials(path)).hasSize(denials + 1);
        assertThat(dslState()).isEqualTo(before);
        assertThat(new DslAuditRows(jdbc).dslRows()).hasSize(dslAuditRows);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("an administrator gets the result of the API itself")
    void adminSucceeds(Endpoint endpoint) {
        HttpResponse<String> response = endpoint.call().apply(admin, fixture);

        assertThat(response.statusCode()).isEqualTo(endpoint.adminStatus());
        if (endpoint.adminCode() != null) {
            assertThat(DslApi.json(response)).containsEntry("code", endpoint.adminCode());
        }
    }

    @Test
    @DisplayName("the list covers the ten APIs of contract C6")
    void coversEveryApi() {
        assertThat(endpoints().map(Endpoint::name))
                .hasSize(10)
                .doesNotHaveDuplicates()
                .contains(
                        "GET /status",
                        "GET /preview",
                        "POST /preview",
                        "DELETE /preview",
                        "POST /preview/generate",
                        "GET /preview/download",
                        "POST /apply",
                        "GET /applied/download",
                        "GET /history",
                        "POST /history/{revisionId}/restore");
    }
}
