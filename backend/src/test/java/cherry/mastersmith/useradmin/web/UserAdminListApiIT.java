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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.domain.SearchText;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.useradmin.testsupport.UserAdminApi;
import cherry.mastersmith.useradmin.testsupport.UserAdminFixtures;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 利用者の一覧の API（{@code GET /api/admin/users}、契約 C3、BR1.1〜BR1.9・BR7.1・BR7.5、NFR1.1・NFR1.3・NFR3.2・NFR8.1）の結合テスト。
 * team.md の必須のテスト（管理の API の認可の 401・403・200、停止中の管理者）を含む。
 *
 * <p>内部DB はクラスごとの一時ディレクトリの H2 で、テストの間でデータを持ち越すため、各テストは自分だけが当たる印（乱数）を
 * メールアドレスに入れ、その印の検索で確かめる。初期管理者は設定で作る（登録した日時が最も古く、利用者 ID が最も小さい）。
 * 時計は基準の時刻より前に戻さない。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthApiTestConfig.class)
class UserAdminListApiIT {

    private static final String INITIAL_ADMIN = "initial-admin-list@example.com";

    private static final List<String> FIELDS = List.of(
            "userId",
            "email",
            "displayName",
            "language",
            "admin",
            "suspended",
            "locked",
            "lockedUntil",
            "resettable",
            "registeredAt",
            "self");

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
        String password = TestDatabase.randomSecret();
        registry.add("mastersmith.auth.initial-admin.email", () -> INITIAL_ADMIN);
        registry.add("mastersmith.auth.initial-admin.password", () -> password);
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
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    private UserAdminApi api;

    private UserAdminFixtures fixtures;

    private long adminId;

    private String adminEmail;

    private String admin;

    @BeforeEach
    void setUp() {
        clock.set(AuthApiTestConfig.START);
        api = new UserAdminApi(port);
        fixtures = new UserAdminFixtures(userAccountService, revocationService, transactionManager, jdbc, port);
        adminEmail = "admin-" + UserAdminFixtures.marker() + "@example.com";
        adminId = fixtures.create(adminEmail, "管理 太郎", true);
        admin = fixtures.login(adminEmail);
    }

    private static Map<String, Object> json(HttpResponse<String> response) {
        return HttpTestClient.json(response);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return (List<Map<String, Object>>) json(response).get("items");
    }

    private static long id(Map<String, Object> item) {
        return ((Number) item.get("userId")).longValue();
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    private static void assertValidationFailed(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response)).containsEntry("code", "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a row has exactly the eleven fields, ISO 8601 UTC times, and no hash, failure count or dummy row")
    void rowShape() {
        String mark = UserAdminFixtures.marker();
        clock.advance(Duration.ofSeconds(5));
        long userId = fixtures.create("shape-" + mark + "@example.com", "形 花子", false);

        HttpResponse<String> response = api.search(admin, mark);

        assertThat(json(response))
                .containsOnlyKeys("items", "page", "size", "total")
                .containsEntry("page", 1)
                .containsEntry("size", 20)
                .containsEntry("total", 1);
        Map<String, Object> row = items(response).getFirst();
        assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(FIELDS);
        assertThat(row)
                .containsEntry("userId", (int) userId)
                .containsEntry("email", "shape-" + mark + "@example.com")
                .containsEntry("displayName", "形 花子")
                .containsEntry("language", "ja")
                .containsEntry("admin", false)
                .containsEntry("suspended", false)
                .containsEntry("locked", false)
                .containsEntry("resettable", false)
                .containsEntry("registeredAt", "2026-09-22T00:00:05Z")
                .containsEntry("self", false);
        assertThat(row.get("lockedUntil")).isNull();
        assertThat(response.body())
                .doesNotContain("$2a$")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("failure")
                .doesNotContainIgnoringCase("hash");
    }

    @Test
    @DisplayName("rows come oldest first, then by user id, 20 per page, with the total over all pages")
    void orderingAndPaging() {
        String mark = UserAdminFixtures.marker();
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            clock.set(AuthApiTestConfig.START.plusSeconds(100 - (i / 2) * 2L));
            expected.add(fixtures.create("order-" + i + "-" + mark + "@example.com", "並び " + i, false));
        }
        List<Long> sorted = new ArrayList<>(expected);
        sorted.sort(Comparator.comparingLong((Long id) -> 100 - (expected.indexOf(id) / 2) * 2L)
                .thenComparingLong(Long::longValue));

        HttpResponse<String> first = api.list(admin, "?q=" + mark);
        HttpResponse<String> second = api.list(admin, "?q=" + mark + "&page=2");

        assertThat(items(first)).extracting(UserAdminListApiIT::id).containsExactlyElementsOf(sorted.subList(0, 20));
        assertThat(items(second)).extracting(UserAdminListApiIT::id).containsExactly(sorted.get(20));
        assertThat(json(first)).containsEntry("total", 21).containsEntry("page", 1);
        assertThat(json(second)).containsEntry("total", 21).containsEntry("page", 2);
    }

    @Test
    @DisplayName(
            "without a search the list starts with the initial admin, includes suspended users and marks only self")
    void wholeList() {
        String mark = UserAdminFixtures.marker();
        long suspended = fixtures.create("suspended-" + mark + "@example.com", "停止 次郎", true);
        fixtures.suspend(suspended);

        List<Map<String, Object>> page1 = items(api.list(admin, ""));
        List<Map<String, Object>> blank = items(api.list(admin, "?q=" + UserAdminApi.encode(" 　 ")));
        HttpResponse<String> found = api.search(admin, mark);

        assertThat(page1.getFirst()).containsEntry("email", INITIAL_ADMIN).containsEntry("admin", true);
        assertThat(page1).hasSizeLessThanOrEqualTo(20);
        assertThat(page1)
                .extracting(item -> (String) item.get("registeredAt"))
                .isSortedAccordingTo(Comparator.comparing(Instant::parse));
        assertThat(blank)
                .extracting(UserAdminListApiIT::id)
                .containsExactlyElementsOf(
                        page1.stream().map(UserAdminListApiIT::id).toList());
        assertThat(json(api.list(admin, "?q=" + UserAdminApi.encode(" "))).get("total"))
                .isEqualTo(json(api.list(admin, "")).get("total"));
        assertThat(items(found))
                .singleElement()
                .satisfies(item ->
                        assertThat(item).containsEntry("suspended", true).containsEntry("self", false));
        assertThat(items(api.search(admin, "admin-")))
                .filteredOn(item -> id(item) == adminId)
                .singleElement()
                .satisfies(item -> assertThat(item).containsEntry("self", true));
        assertThat(items(api.search(admin, "admin-")))
                .filteredOn(item -> id(item) != adminId)
                .allSatisfy(item -> assertThat(item).containsEntry("self", false));
    }

    @Test
    @DisplayName("the lock view follows the clock: locked, exactly at the unlock time, past it, no row, one failure")
    void lockView() {
        String mark = UserAdminFixtures.marker();
        Instant now = AuthApiTestConfig.START.plus(Duration.ofHours(1));
        long locked = fixtures.create("a-locked-" + mark + "@example.com", "ロック 中", false);
        long exact = fixtures.create("b-exact-" + mark + "@example.com", "ちょうど", false);
        long past = fixtures.create("c-past-" + mark + "@example.com", "過ぎた", false);
        long noRow = fixtures.create("d-norow-" + mark + "@example.com", "行なし", false);
        long one = fixtures.create("e-one-" + mark + "@example.com", "一回", false);
        Instant until = now.plus(Duration.ofMinutes(10));
        fixtures.lockState(locked, 5, until);
        fixtures.lockState(exact, 5, now);
        fixtures.lockState(past, 5, now.minusSeconds(1));
        fixtures.lockState(one, 1, null);
        clock.set(now);
        admin = fixtures.login(adminEmail);

        Map<Long, Map<String, Object>> rows = new java.util.HashMap<>();
        items(api.search(admin, mark)).forEach(item -> rows.put(id(item), item));

        assertThat(rows.get(locked))
                .containsEntry("locked", true)
                .containsEntry("lockedUntil", "2026-09-22T01:10:00Z")
                .containsEntry("resettable", true);
        for (long notLocked : new long[] {exact, past}) {
            assertThat(rows.get(notLocked)).containsEntry("locked", false).containsEntry("resettable", true);
            assertThat(rows.get(notLocked).get("lockedUntil")).isNull();
        }
        assertThat(rows.get(noRow)).containsEntry("locked", false).containsEntry("resettable", false);
        assertThat(rows.get(one)).containsEntry("locked", false).containsEntry("resettable", true);
        assertThat(rows.get(one).get("lockedUntil")).isNull();
    }

    @Test
    @DisplayName("a page after the last one and a search that hits nothing are 200 with empty items and the total")
    void emptyPages() {
        String mark = UserAdminFixtures.marker();
        fixtures.create("beyond-" + mark + "@example.com", "後ろ", false);

        HttpResponse<String> beyond = api.list(admin, "?q=" + mark + "&page=2");
        HttpResponse<String> far = api.list(admin, "?page=999999999");
        HttpResponse<String> none = api.search(admin, UserAdminFixtures.marker());

        assertThat(items(beyond)).isEmpty();
        assertThat(json(beyond)).containsEntry("total", 1).containsEntry("page", 2);
        assertThat(items(far)).isEmpty();
        assertThat(((Number) json(far).get("total")).longValue()).isPositive();
        assertThat(items(none)).isEmpty();
        assertThat(json(none)).containsEntry("total", 0).containsEntry("page", 1);
    }

    @Test
    @DisplayName("a bad page is 400 without field errors, and a search over 254 code points is 400 q TOO_LONG")
    void inputErrors() {
        String exactly = "検".repeat(SearchText.MAX_CODE_POINTS - 1) + "😀";
        String over = "x" + exactly;

        for (String page : new String[] {"0", "abc", "", "1234567890", "-1"}) {
            HttpResponse<String> response = api.list(admin, "?page=" + page);
            assertValidationFailed(response);
            assertThat(json(response)).doesNotContainKey("fieldErrors");
        }
        HttpResponse<String> ok = api.search(admin, " " + exactly + " ");
        HttpResponse<String> tooLong = api.search(admin, over);
        HttpResponse<String> both = api.list(admin, "?page=0&q=" + UserAdminApi.encode(over));

        assertThat(ok.statusCode()).isEqualTo(200);
        assertValidationFailed(tooLong);
        assertThat(json(tooLong).get("fieldErrors")).isEqualTo(List.of(Map.of("field", "q", "reason", "TOO_LONG")));
        assertThat(tooLong.body()).doesNotContain("検検検").doesNotContain("xxx");
        assertValidationFailed(both);
        assertThat(json(both)).doesNotContainKey("fieldErrors");
    }

    @Test
    @DisplayName("a request line over 8KB is a text/html 400 from Tomcat and never reaches the application")
    void oversizedRequestLine() {
        int before = auditCount();

        HttpResponse<String> response = api.list(admin, "?page=" + "1".repeat(9_000));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
        assertThat(response.body()).doesNotContain("VALIDATION_FAILED");
        assertThat(auditCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("401 without a token, 403 ACCESS_DENIED for a member, 200 for an admin, 401 for a suspended admin")
    void authorization() {
        String mark = UserAdminFixtures.marker();
        String memberEmail = "member-" + mark + "@example.com";
        fixtures.create(memberEmail, "一般 三郎", false);
        String member = fixtures.login(memberEmail);
        String suspendedEmail = "suspended-admin-" + mark + "@example.com";
        long suspendedAdmin = fixtures.create(suspendedEmail, "停止 管理者", true);
        String suspendedToken = fixtures.login(suspendedEmail);
        fixtures.suspend(suspendedAdmin);
        int before = auditCount();
        String query = "?q=" + mark;

        HttpResponse<String> anonymous = api.list(null, query);
        HttpResponse<String> forbidden = api.list(member, query);
        HttpResponse<String> allowed = api.list(admin, query);
        HttpResponse<String> suspended = api.list(suspendedToken, query);

        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(json(anonymous)).containsEntry("code", "AUTHENTICATION_REQUIRED");
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(json(forbidden)).containsEntry("code", "ACCESS_DENIED");
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(suspended.statusCode()).isEqualTo(401);
        assertThat(json(suspended)).containsEntry("code", "AUTHENTICATION_REQUIRED");
        assertThat(forbidden.body()).doesNotContain(mark);
        List<Map<String, Object>> added = jdbc.queryForList(
                "SELECT event_type, failure_reason, request_path FROM audit_events ORDER BY audit_event_id OFFSET ? ROWS",
                before);
        assertThat(added)
                .as("既存のアクセスの拒否（トークンなし・管理者でない）だけが残り、停止中は残らず、パスに q が無い")
                .extracting(
                        row -> row.get("EVENT_TYPE") + " " + row.get("FAILURE_REASON") + " " + row.get("REQUEST_PATH"))
                .containsExactly(
                        "ACCESS_DENIED TOKEN_MISSING " + UserAdminApi.PATH,
                        "ACCESS_DENIED NOT_ADMIN " + UserAdminApi.PATH);
    }
}
