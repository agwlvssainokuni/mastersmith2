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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.BITS;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.DUP_VIEW;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.GHOST;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.ITEM;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.LEGACY;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.LINE;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.MIXED;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.NO_KEY;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.ODD_BRANCH;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.OTHER_KEY;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.PARTIAL;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.UUID_1;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.WEIRD;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables.ZONED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import cherry.mastersmith.businessdata.service.BusinessDataReadService;
import cherry.mastersmith.businessdata.testsupport.BusinessDataReadTables;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.RandomFieldMask;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import cherry.mastersmith.targetdb.testsupport.TargetDbTestDatabase;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 業務データの定義・一覧と検索・詳細を、3種類の対象DB で確かめる共通の結合テスト（Step 15、NFR3.1・NFR3.3・NFR2.2・NFR2.5・NFR7.1、
 * BR2〜BR8、D1: A）。子のクラスで DB の種類を決める。
 *
 * <p>アプリは読み取りだけのアカウントで対象DB を読む。対象DB のサーバーのタイムゾーンは、PostgreSQL は夏時間のある地域
 * （America/New_York）、MySQL・MariaDB は {@code -05:00} にする（名前のタイムゾーンの表を持たないため）。テストの JVM は Asia/Tokyo。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// 拡張の登録の順は、ログの取り込み（OutputCaptureExtension）を先、コンテナの実行環境の確かめ（ContainerRuntimeCheck）を後にする
// （ExtensionOrderArchitectureTest）。
@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})
@SuppressWarnings("unchecked")
abstract class AbstractBusinessDataReadIT {

    private final TargetDbTestDatabase database = TargetDbTestDatabase.of(product());

    private final String schema = TargetDbTestDatabase.uniqueName("biz_");

    private final String reader = TargetDbTestDatabase.uniqueName("reader_");

    private final String readerPassword = TestDatabase.randomSecret();

    private BusinessDataTestApp app;

    private Actor member;

    /**
     * 確かめる DB の種類を返す。
     *
     * @return 種類
     */
    abstract DatabaseProduct product();

    private boolean postgres() {
        return product() == DatabaseProduct.POSTGRESQL;
    }

    @BeforeAll
    void start(@TempDir Path dir) {
        database.start();
        database.createSchema(schema);
        if (postgres()) {
            database.execute("ALTER DATABASE " + database.q(database.connectDatabase(schema))
                    + " SET timezone TO 'America/New_York'");
        } else {
            database.execute("SET GLOBAL time_zone = '-05:00'");
        }
        BusinessDataReadTables tables = new BusinessDataReadTables(database, schema);
        tables.create();
        database.createReadOnlyAccount(reader, readerPassword, schema);
        app = BusinessDataTestApp.start(
                dir.resolve("main"),
                new BusinessDataTestApp.Target(
                        product(),
                        database.host(),
                        database.port(),
                        database.connectDatabase(schema),
                        schema,
                        reader,
                        readerPassword));
        app.installDsl(tables.model());
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(schema), new PermissionValues(MainPermission.READ, false, false));
        settings.put(
                PermissionTarget.column(schema, ITEM, "hidden"), new PermissionValues(MainPermission.NONE, null, null));
        member = app.reader(settings);
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
            database.dropAccount(reader);
            database.dropSchema(schema);
        } finally {
            database.stop();
        }
    }

    // ── 送る道具 ──

    private Map<String, Object> body(String table, Object... extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", schema);
        body.put("table", table);
        for (int i = 0; i < extra.length; i += 2) {
            body.put((String) extra[i], extra[i + 1]);
        }
        return body;
    }

    private HttpResponse<String> definitionResponse(String table) {
        return app.post(BusinessDataPaths.DEFINITION, member.token(), body(table));
    }

    private Map<String, Object> definition(String table) {
        HttpResponse<String> response = definitionResponse(table);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return HttpTestClient.json(response);
    }

    private HttpResponse<String> searchResponse(
            String table, List<Map<String, Object>> conditions, Object sort, int page, int size) {
        Map<String, Object> body = body(table, "conditions", conditions, "page", page, "size", size);
        if (sort != null) {
            body.put("sort", sort);
        }
        return app.post(BusinessDataPaths.SEARCH, member.token(), body);
    }

    private Map<String, Object> search(
            String table, List<Map<String, Object>> conditions, Object sort, int page, int size) {
        HttpResponse<String> response = searchResponse(table, conditions, sort, page, size);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return HttpTestClient.json(response);
    }

    private Map<String, Object> search(String table, Map<String, Object> condition) {
        return search(table, List.of(condition), null, 1, 20);
    }

    private HttpResponse<String> getResponse(String table, Map<String, String> key) {
        return app.post(BusinessDataPaths.GET, member.token(), body(table, "key", key));
    }

    private Map<String, Object> get(String table, Map<String, String> key) {
        HttpResponse<String> response = getResponse(table, key);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return HttpTestClient.json(response);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(Map<String, Object> rowOrDetail) {
        return (Map<String, Object>) rowOrDetail.get("values");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> key(Map<String, Object> row) {
        return (Map<String, Object>) row.get("key");
    }

    private static List<Object> ids(Map<String, Object> page) {
        return items(page).stream().map(row -> values(row).get("id")).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> names(Map<String, Object> definition, String field) {
        return ((List<Map<String, Object>>) definition.get(field))
                .stream().map(c -> (String) c.get("column")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> columnOf(Map<String, Object> definition, String field, String column) {
        return ((List<Map<String, Object>>) definition.get(field))
                .stream()
                        .filter(c -> column.equals(c.get("column")))
                        .findFirst()
                        .orElseThrow();
    }

    private static Map<String, Object> eq(String column, String value) {
        return Map.of("column", column, "value", value);
    }

    private static Map<String, Object> range(String column, String from, String to) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("column", column);
        condition.put("from", from);
        condition.put("to", to);
        return condition;
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(HttpTestClient.json(response)).containsEntry("code", code);
    }

    // ── 定義 ──

    @Test
    @DisplayName("the definition hides NONE columns, follows list and detail visibility and narrows the search fields")
    void definitionShape() {
        Map<String, Object> definition = definition(ITEM);

        assertThat(names(definition, "listColumns"))
                .doesNotContain("hidden", "at")
                .contains("memo", "secret", "photo");
        assertThat(names(definition, "detailColumns")).doesNotContain("hidden").contains("at");
        List<String> searchFields = names(definition, "searchFields");
        assertThat(searchFields)
                .doesNotContain("hidden", "secret", "photo", "memo")
                .contains("id", "name", "kind", WEIRD);
        if (product() == DatabaseProduct.MARIADB) {
            assertThat(searchFields).as("MariaDB の json は文字").contains("doc");
        } else {
            assertThat(searchFields).as("BR7.9 の json は EQUALS から外す").doesNotContain("doc");
        }
        assertThat(columnOf(definition, "listColumns", "photo")).containsEntry("valueType", "BINARY");
        assertThat(columnOf(definition, "listColumns", "secret")).containsEntry("masked", true);
        assertThat(columnOf(definition, "listColumns", "flag")).containsEntry("valueType", "BOOLEAN");
        assertThat(columnOf(definition, "listColumns", "at_tz")).containsEntry("valueType", "DATETIME_TZ");
        assertThat(columnOf(definition, "listColumns", "kind")).containsEntry("valueType", "OTHER");
        assertThat(definition)
                .containsEntry("operations", List.of("LIST", "DETAIL"))
                .containsEntry("writeConfigured", false);
        assertThat(definition.toString()).doesNotContain(UUID_1);
    }

    @Test
    @DisplayName("every column of the definition carries the validation items, with required and nullable always set")
    void definitionValidationItems() {
        Map<String, Object> definition = definition(ITEM);
        List<String> items = List.of(
                "formPart",
                "required",
                "nullable",
                "minLength",
                "maxLength",
                "precision",
                "scale",
                "min",
                "max",
                "choices",
                "hasPattern");

        // Intent 261016-business-data-views の U2 の BR14.1・BR14.2: 検証の材料はすべてのカラムで省かずに出す（型に当たらない項目は
        // null、required・nullable は必ず真偽値）。
        for (String field : List.of("listColumns", "detailColumns")) {
            assertThat((List<Map<String, Object>>) definition.get(field)).allSatisfy(column -> {
                assertThat(column).containsKeys(items.toArray(String[]::new));
                assertThat(column.get("required")).isInstanceOf(Boolean.class);
                assertThat(column.get("nullable")).isInstanceOf(Boolean.class);
                assertThat(column.get("hasPattern")).isInstanceOf(Boolean.class);
            });
        }
        // この利用者は READ のため入力できず、検証の値は null（nullable だけは DSL の値）。入力できるカラムの値は
        // AbstractBusinessDataWriteIT#definitionForWrites が確かめる。
        assertThat(columnOf(definition, "detailColumns", "name"))
                .containsEntry("editableOnCreate", false)
                .containsEntry("required", false)
                .containsEntry("nullable", true)
                .containsEntry("maxLength", null)
                .containsEntry("choices", List.of())
                .containsEntry("hasPattern", false);
    }

    // ── 値の受け渡し ──

    @Test
    @DisplayName("boundary values come back as text without losing a digit and password, binary and NONE are left out")
    void boundaryValues() {
        Map<String, Object> detail = values(get(ITEM, Map.of("id", "1")));

        assertThat(detail)
                .containsEntry("big", "9223372036854775807")
                .containsEntry("amount", "1234567890123456789012345678.0123456789")
                .containsEntry("flag", "true")
                .containsEntry("day", "2026-01-15")
                .containsEntry("at", "2026-03-08T02:30:00.123456")
                .containsEntry("at_tz", "2026-03-08T01:30:00+00:00")
                .containsEntry("kind", postgres() ? UUID_1 : "A")
                .containsEntry(WEIRD, "w1")
                .containsEntry(MIXED, "M1")
                .containsEntry("name", BusinessDataReadTables.NAME_1)
                .doesNotContainKeys("secret", "photo", "hidden");
        assertThat((String) detail.get("doc")).contains("\"a\"");
        Map<String, Object> second = values(get(ITEM, Map.of("id", "2")));
        assertThat(second)
                .containsEntry("big", "-9223372036854775808")
                .containsEntry("amount", "-0.0000000001")
                .containsEntry("flag", "false")
                .containsEntry("at_tz", null)
                .containsEntry(MIXED, null);
        assertThat(values(get(ITEM, Map.of("id", "4")))).containsEntry("name", "");
        assertThat(values(get(ITEM, Map.of("id", "3")))).containsEntry("name", null);
    }

    @Test
    @DisplayName("long text is cut at 100 code points in the list and 10,000 in the detail, with the mark")
    void truncation() {
        Map<String, Object> page = search(ITEM, List.of(), null, 1, 20);
        Map<Object, Map<String, Object>> rows = new LinkedHashMap<>();
        items(page).forEach(row -> rows.put(values(row).get("id"), row));

        assertThat((String) values(rows.get("1")).get("memo")).hasSize(100);
        assertThat((List<?>) rows.get("1").get("truncatedColumns")).isEmpty();
        assertThat((String) values(rows.get("2")).get("memo")).isEqualTo("い".repeat(100));
        assertThat((List<Object>) rows.get("2").get("truncatedColumns")).containsExactly("memo");
        assertThat((String) values(rows.get("5")).get("memo")).isEqualTo("x".repeat(100));
        Map<String, Object> exact = get(ITEM, Map.of("id", "3"));
        assertThat((String) values(exact).get("memo")).hasSize(10_000);
        assertThat((List<?>) exact.get("truncatedColumns")).isEmpty();
        Map<String, Object> over = get(ITEM, Map.of("id", "4"));
        assertThat((String) values(over).get("memo")).isEqualTo("え".repeat(10_000));
        assertThat((List<Object>) over.get("truncatedColumns")).containsExactly("memo");
        assertThat((String) values(get(ITEM, Map.of("id", "5"))).get("memo")).hasSize(10_000);
    }

    // ── 検索 ──

    @Test
    @DisplayName("partial match treats percent, underscore, exclamation, backslash, quotes and newlines as characters")
    void containsIsLiteral() {
        assertThat(ids(search(ITEM, eq("name", "50%_!")))).containsExactly("1");
        assertThat(ids(search(ITEM, eq("name", "%")))).containsExactly("1");
        assertThat(ids(search(ITEM, eq("name", "_")))).containsExactly("1");
        assertThat(ids(search(ITEM, eq("name", "!")))).containsExactly("1");
        assertThat(ids(search(ITEM, eq("name", "\\")))).containsExactly("2");
        assertThat(ids(search(ITEM, eq("name", "'s \"q\"; --\n`")))).containsExactly("5");
    }

    @Test
    @DisplayName("equality, range and choice are bound with their types on every database")
    void typedConditions() {
        assertThat(ids(search(ITEM, eq("id", "3")))).containsExactly("3");
        assertThat(ids(search(ITEM, range("big", "0", null)))).containsExactly("1");
        assertThat(ids(search(ITEM, range("big", null, "-1")))).containsExactly("2");
        String amount = "1234567890123456789012345678.0123456789";
        assertThat(ids(search(ITEM, range("amount", amount, amount)))).containsExactly("1");
        assertThat(ids(search(ITEM, Map.of("column", "flag", "values", List.of("true")))))
                .containsExactly("1");
        assertThat(ids(search(ITEM, Map.of("column", "flag", "values", List.of("false")))))
                .containsExactly("2");
        assertThat(ids(search(ITEM, range("day", "2026-01-20", null)))).containsExactly("2");
        assertThat(ids(search(ITEM, range("at", null, "2026-03-08T02:30:00.123456"))))
                .containsExactly("1");
        assertThat(ids(search(ITEM, eq("kind", postgres() ? UUID_1 : "A")))).containsExactly("1");
        assertThat(ids(search(ITEM, eq(WEIRD, "w2")))).containsExactly("2");
        Map<String, Object> injected = search(ITEM, eq(WEIRD, "x' OR '1'='1"));
        assertThat(injected).containsEntry("total", 0);
    }

    @Test
    @DisplayName("zoned values without an offset, with Z and with +09:00 hit the same row and come back in UTC")
    void zonedValues() {
        for (String value : List.of("2026-03-08T01:30:00", "2026-03-08T01:30:00Z", "2026-03-08T10:30:00+09:00")) {
            Map<String, Object> page = search(ITEM, eq("at_tz", value));
            assertThat(ids(page)).as(value).containsExactly("1");
            assertThat(values(items(page).getFirst())).containsEntry("at_tz", "2026-03-08T01:30:00+00:00");
        }
    }

    @Test
    @DisplayName("PostgreSQL timetz values are compared and returned in UTC")
    void timeWithTimeZone() {
        assumeTrue(postgres(), "timetz は PostgreSQL だけ");

        Map<String, Object> page = search(ZONED, eq("tt", "01:30:00Z"));

        assertThat(ids(page)).containsExactly("1", "2");
        assertThat(items(page)).allSatisfy(row -> assertThat(values(row)).containsEntry("tt", "01:30:00+00:00"));
    }

    // ── 並べ替えとページ ──

    @Test
    @DisplayName("nulls come last in ascending and first in descending order")
    void nullOrdering() {
        List<Object> ascending = ids(search(ITEM, List.of(), Map.of("column", "name", "direction", "ASC"), 1, 20));
        List<Object> descending = ids(search(ITEM, List.of(), Map.of("column", "name", "direction", "DESC"), 1, 20));

        assertThat(ascending).hasSize(5).last().isEqualTo("3");
        assertThat(descending).hasSize(5).first().isEqualTo("3");
        assertThat(ids(search(ITEM, List.of(), Map.of("column", WEIRD, "direction", "DESC"), 1, 20)))
                .hasSize(5);
    }

    @Test
    @DisplayName("pages of a composite key table do not overlap, the page past the end is empty with the total")
    void pages() {
        Map<String, String> sort = Map.of("column", "qty", "direction", "ASC");
        Map<String, Object> first = search(LINE, List.of(), sort, 1, 20);
        Map<String, Object> second = search(LINE, List.of(), sort, 2, 20);
        Map<String, Object> third = search(LINE, List.of(), sort, 3, 20);

        assertThat(first).containsEntry("total", 25);
        assertThat(items(first)).hasSize(20);
        assertThat(items(second)).hasSize(5);
        assertThat(third).containsEntry("total", 25).containsEntry("items", List.of());
        Set<Map<String, Object>> keys = new HashSet<>();
        List<Map<String, Object>> all = new ArrayList<>(items(first));
        all.addAll(items(second));
        all.forEach(row -> keys.add(key(row)));
        assertThat(keys).as("重ならない").hasSize(25);
        assertThat(keys).contains(Map.of("order_no", "1", "branch", ODD_BRANCH));
        assertThat(items(search(LINE, List.of(), null, 1, 100)))
                .as("読む行は件数を超えない")
                .hasSize(25);
    }

    // ── 詳細と主キー ──

    @Test
    @DisplayName("the detail takes keys with slashes, percent signs, question marks, hashes, spaces and quotes")
    void oddKeyValues() {
        Map<String, Object> detail = get(LINE, Map.of("order_no", "1", "branch", ODD_BRANCH));

        assertThat(key(detail)).containsEntry("branch", ODD_BRANCH);
        assertThat(values(detail)).containsEntry("qty", "1");
        HttpResponse<String> missing = getResponse(LINE, Map.of("order_no", "1"));
        assertProblem(missing, 400, "VALIDATION_FAILED");
        assertThat(HttpTestClient.json(missing).get("fieldErrors"))
                .isEqualTo(List.of(Map.of("field", "branch", "reason", "KEY_MISMATCH")));
    }

    @Test
    @DisplayName("0 rows are 404, 2 rows of a view key are 409 and a table without a key refuses the detail")
    void detailRowCounts() {
        assertProblem(getResponse(DUP_VIEW, Map.of("order_no", "99")), 404, "BUSINESS_ROW_NOT_FOUND");
        assertProblem(getResponse(DUP_VIEW, Map.of("order_no", "1")), 409, "BUSINESS_ROW_NOT_UNIQUE");
        assertProblem(getResponse(ITEM, Map.of("id", "999")), 404, "BUSINESS_ROW_NOT_FOUND");
        assertProblem(getResponse(NO_KEY, Map.of("txt", "one")), 409, "BUSINESS_ROW_OPERATION_NOT_ALLOWED");
        assertThat(definition(NO_KEY)).containsEntry("restrictionReason", "NO_PRIMARY_KEY");
        assertThat(definition(DUP_VIEW))
                .containsEntry("restrictionReason", "VIEW")
                .containsEntry("view", true);
        Map<String, Object> noKeyPage = search(NO_KEY, List.of(), null, 1, 20);
        assertThat(noKeyPage).containsEntry("total", 2);
        assertThat(items(noKeyPage))
                .allSatisfy(row -> assertThat(row.get("key")).isNull());
    }

    @Test
    @DisplayName("an OTHER type key can be searched by equality and used to read the detail")
    void otherKey() {
        String value = postgres() ? UUID_1 : "2026";

        assertThat(search(OTHER_KEY, eq("k", value))).containsEntry("total", 1);
        assertThat(values(get(OTHER_KEY, Map.of("k", value)))).containsEntry("v", "other");
    }

    // ── 定義との食い違い ──

    @Test
    @DisplayName("a table or a column of the DSL that the database lacks is 409 MISMATCH without names in the body")
    void mismatch() {
        HttpResponse<String> ghost = searchResponse(GHOST, List.of(), null, 1, 20);
        HttpResponse<String> partial = searchResponse(PARTIAL, List.of(), null, 1, 20);

        assertProblem(ghost, 409, "BUSINESS_TARGET_DB_MISMATCH");
        assertProblem(partial, 409, "BUSINESS_TARGET_DB_MISMATCH");
        assertThat(partial.body()).doesNotContain("phantom").doesNotContain(schema);
    }

    @Test
    @DisplayName("MySQL and MariaDB zero dates and out-of-range times are 409 MISMATCH logged as READ_CONVERSION")
    void unreadableStoredValues(CapturedOutput output) {
        assumeTrue(!postgres(), "ゼロ日付と 838 時間は MySQL・MariaDB だけ");

        HttpResponse<String> list = searchResponse(LEGACY, List.of(), null, 1, 20);
        HttpResponse<String> zeroDate = getResponse(LEGACY, Map.of("id", "1"));
        HttpResponse<String> longTime = getResponse(LEGACY, Map.of("id", "2"));

        assertProblem(list, 409, "BUSINESS_TARGET_DB_MISMATCH");
        assertProblem(zeroDate, 409, "BUSINESS_TARGET_DB_MISMATCH");
        assertProblem(longTime, 409, "BUSINESS_TARGET_DB_MISMATCH");
        // 乱数のトレースID と type の URL の番号に 838 が偶然に含まれうるため、伏せてから確かめる（U3 の B6、N-32）。
        assertThat(RandomFieldMask.mask(list.body() + zeroDate.body() + longTime.body()))
                .doesNotContain("0000-00-00")
                .doesNotContain("838");
        List<Map<String, Object>> warns = JsonLogRecords.parse(output.getAll()).stream()
                .filter(r -> BusinessDataReadService.class.getName().equals(r.get("logger")))
                .filter(r -> "READ_CONVERSION".equals(r.get("reason")))
                .toList();
        assertThat(warns).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("MySQL and MariaDB bit(8) and spatial columns are BINARY: no value, no search, no sort")
    void bitsAndSpatial() {
        assumeTrue(!postgres(), "bit と空間の型は MySQL・MariaDB だけ");

        Map<String, Object> definition = definition(BITS);
        Map<String, Object> page = search(BITS, List.of(), null, 1, 20);

        assertThat(columnOf(definition, "listColumns", "b")).containsEntry("valueType", "BINARY");
        assertThat(columnOf(definition, "listColumns", "g")).containsEntry("valueType", "BINARY");
        assertThat(names(definition, "searchFields")).doesNotContain("b", "g");
        assertThat((List<Object>) definition.get("sortableColumns")).doesNotContain("b", "g");
        assertThat(values(items(page).getFirst())).containsOnlyKeys("id", "v");
    }
}
