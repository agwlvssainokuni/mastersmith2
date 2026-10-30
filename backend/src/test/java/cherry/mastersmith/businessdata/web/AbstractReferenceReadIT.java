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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_COUNTS;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_LENGTHS;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_MISC;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_PICK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_SHAPES;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SUPPLEMENTARY;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.domain.ReferenceCheckResult;
import cherry.mastersmith.businessdata.domain.ReferenceCountState;
import cherry.mastersmith.businessdata.domain.ReferenceValue;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.service.ReferenceLookup;
import cherry.mastersmith.businessdata.service.TableAccessResolution;
import cherry.mastersmith.businessdata.service.TableAccessResolver;
import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.domain.ReadPurpose;
import cherry.mastersmith.targetdb.domain.TargetReadOutcome;
import cherry.mastersmith.targetdb.domain.TargetReadSession;
import cherry.mastersmith.targetdb.service.TargetReadAccess;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * 定義の取得の参照の項目と、参照先の検索・ラベルの1件読み・保存前の確かめの口（{@code check}・{@code countState}）を、3種類の対象DB で
 * 確かめる共通の結合テスト（Intent 261029-reference-lookup-cleanup の U1。B1 の Step 12 の T-B1〜T-B3・T-B6・T-B8・T-B9・T-B10 の定義の
 * 分、B2 の Step 21 の T-B4〜T-B7・T-B9 の残り、(c)(e)(h)(i)、AC1.3.1）。子のクラスで DB の種類を決める。確かめの口は U2 がまだ呼ばない
 * ため、アプリの部品（解決の口・読み取りの口・{@link ReferenceLookup}）を直接呼ぶ。
 *
 * <p>表はクラスごとに一意の名前のスキーマ（MySQL・MariaDB はデータベース）に作り、終わったら消す（{@link BusinessDataSampleSchema}）。
 * アプリは読み取りだけのアカウントで対象DB を読む。値はすべて架空。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
@SuppressWarnings("unchecked")
abstract class AbstractReferenceReadIT {

    private BusinessDataSampleSchema sample;

    private BusinessDataTestApp app;

    private Actor member;

    /**
     * 確かめる DB の種類を返す。
     *
     * @return 種類
     */
    abstract DatabaseProduct product();

    @BeforeAll
    void start(@TempDir Path dir) {
        sample = new BusinessDataSampleSchema(product());
        sample.start();
        ReferenceTables tables = new ReferenceTables(sample);
        tables.create();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target());
        app.installDsl(sample.model(tables.tables()));
        member = app.reader(tables.settings());
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
        } finally {
            if (sample != null) {
                sample.stop();
            }
        }
    }

    // ── 送る道具 ──

    private HttpResponse<String> definitionResponse(String table, String purpose) {
        Map<String, Object> body = sample.body(table);
        if (purpose != null) {
            body.put("purpose", purpose);
        }
        return app.post(BusinessDataPaths.DEFINITION, member.token(), body);
    }

    private Map<String, Object> definition(String table, String purpose) {
        HttpResponse<String> response = definitionResponse(table, purpose);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return HttpTestClient.json(response);
    }

    /** 詳細の列から、カラムの名前ごとの {@code reference} を引く（null を含む）。 */
    private static Map<String, Map<String, Object>> references(Map<String, Object> definition) {
        Map<String, Map<String, Object>> references = new LinkedHashMap<>();
        for (Map<String, Object> column : (List<Map<String, Object>>) definition.get("detailColumns")) {
            assertThat(column).containsKey("reference");
            references.put((String) column.get("column"), (Map<String, Object>) column.get("reference"));
        }
        return references;
    }

    private static List<Map<String, Object>> options(Map<String, Object> reference) {
        assertThat(reference.get("status")).isEqualTo("AVAILABLE");
        return (List<Map<String, Object>>) reference.get("options");
    }

    private static List<Object> values(Map<String, Object> reference) {
        return options(reference).stream().map(o -> o.get("value")).toList();
    }

    // ── 確かめ ──

    @Test
    @DisplayName("199 and 200 rows are AVAILABLE, 201 and 10,000 rows are TOO_MANY without options (T-B1)")
    void rowCountBoundaries() {
        Map<String, Map<String, Object>> refs = references(definition(SRC_COUNTS, "CREATE"));

        assertThat(options(refs.get("c199"))).hasSize(199);
        assertThat(options(refs.get("c200"))).hasSize(200);
        assertThat(values(refs.get("c200")).get(0)).isEqualTo("V00001");
        for (String column : List.of("c201", "c10000")) {
            assertThat(refs.get(column)).containsEntry("status", "TOO_MANY").doesNotContainKey("options");
        }
        assertThat(refs.get("id")).isNull();
        assertThat(refs.get("name")).isNull();
    }

    @Test
    @DisplayName("rows whose value is longer than 10,000 characters are left out before counting (T-B2, T-B3)")
    void longValuesAreLeftOut() {
        Map<String, Map<String, Object>> refs = references(definition(SRC_LENGTHS, "UPDATE"));

        assertThat(values(refs.get("m200")))
                .hasSize(200)
                .allSatisfy(v -> assertThat((String) v).startsWith("V"));
        assertThat(refs.get("m201")).containsEntry("status", "TOO_MANY");
    }

    @Test
    @DisplayName("values of 10,000 characters in any plane come back whole, labels are cut at 100 and NULL comes last")
    void lengthBoundaries() {
        List<Map<String, Object>> edge =
                options(references(definition(SRC_LENGTHS, "CREATE")).get("edge"));

        assertThat(edge)
                .extracting(o -> (String) o.get("value"))
                .containsExactlyInAnyOrder(
                        "a".repeat(10_000),
                        "あ".repeat(10_000),
                        SUPPLEMENTARY.repeat(10_000),
                        "b".repeat(100),
                        "c".repeat(101),
                        "nl");
        Map<String, String> labels = new LinkedHashMap<>();
        edge.forEach(o -> labels.put((String) o.get("value"), (String) o.get("label")));
        assertThat(labels.get("b".repeat(100))).isEqualTo("ら".repeat(100));
        assertThat(labels.get("c".repeat(101))).as("ラベルは 100 字で切る").isEqualTo("り".repeat(100));
        assertThat(edge.get(edge.size() - 1)).containsEntry("value", "nl").containsEntry("label", null);
    }

    @Test
    @DisplayName("big integers and long decimals keep every digit and equal decimals keep only the first label")
    void typedValues() {
        Map<String, Map<String, Object>> refs = references(definition(SRC_SHAPES, "CREATE"));

        assertThat(values(refs.get("num")))
                .containsExactlyInAnyOrder(ReferenceTables.BIG_MAX, "-9223372036854775808", "1");
        List<Map<String, Object>> decs = options(refs.get("dec"));
        assertThat(decs).hasSize(2);
        assertThat(decs).extracting(o -> o.get("label")).containsExactlyInAnyOrder("big", "x");
        assertThat(decs.stream().map(o -> (String) o.get("value")))
                .anySatisfy(
                        v -> assertThat(new java.math.BigDecimal(v)).isEqualByComparingTo(ReferenceTables.BIG_DECIMAL));
    }

    @Test
    @DisplayName("equal values keep the first label in order, letter case differences stay distinct")
    void duplicates() {
        List<Map<String, Object>> dup =
                options(references(definition(SRC_SHAPES, "CREATE")).get("dup"));

        assertThat(dup)
                .extracting(o -> o.get("value"), o -> o.get("label"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("K", "a"), org.assertj.core.groups.Tuple.tuple("k", "c"));
    }

    @Test
    @DisplayName("quoted names, composite keys, tables without a key, views and self references are read as they are")
    void shapes() {
        Map<String, Map<String, Object>> refs = references(definition(SRC_SHAPES, "CREATE"));

        assertThat(options(refs.get("quoted")))
                .singleElement()
                .satisfies(o -> assertThat(o).containsEntry("value", "it's").containsEntry("label", "say \"hi\""));
        assertThat(options(refs.get("comp")))
                .extracting(o -> o.get("value"), o -> o.get("label"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("B1", "one"),
                        org.assertj.core.groups.Tuple.tuple("B2", "three"));
        assertThat(values(refs.get("nokey"))).containsExactlyInAnyOrder("N1", "N2");
        assertThat(values(refs.get("view"))).hasSize(199);
        assertThat(options(refs.get("parent")))
                .extracting(o -> o.get("value"), o -> o.get("label"))
                .as("自己参照は同じ写しで読むだけ（並びは各 DB の照合順序に従う）")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("1", "親"), org.assertj.core.groups.Tuple.tuple("2", "子"));
    }

    @Test
    @DisplayName("labels with the same first 100 characters are ordered by value (T-B8)")
    void samePrefixOrder() {
        List<Map<String, Object>> prefix =
                options(references(definition(SRC_MISC, "CREATE")).get("prefix"));

        assertThat(prefix).extracting(o -> o.get("value")).containsExactly("p3", "p1", "p2");
        assertThat(prefix.get(1).get("label")).isEqualTo("同".repeat(100));
    }

    @Test
    @DisplayName("2,000 rows with 200 long labels sort without running out of memory and are TOO_MANY (T-B9)")
    void longLabelsSort() {
        assertThat(references(definition(SRC_MISC, "CREATE")).get("wide")).containsEntry("status", "TOO_MANY");
    }

    @Test
    @DisplayName("untrusted values and labels come back as the same strings (T-B10)")
    void untrustedValues() {
        Map<String, Object> xss =
                options(references(definition(SRC_MISC, "CREATE")).get("xss")).get(0);

        assertThat(xss)
                .containsEntry("value", ReferenceTables.XSS_VALUE)
                .containsEntry("label", ReferenceTables.XSS_LABEL);
    }

    @Test
    @DisplayName("an unreadable target is NO_PERMISSION in a 200 and a lookup carries its picker (AC1.3.1)")
    void noPermissionAndLookup() {
        Map<String, Object> definition = definition(SRC_MISC, "CREATE");
        Map<String, Map<String, Object>> refs = references(definition);

        assertThat(refs.get("secret"))
                .containsEntry("kind", "REFERENCE")
                .containsEntry("status", "NO_PERMISSION")
                .doesNotContainKey("options")
                .doesNotContainKey("picker");
        assertThat(definition.toString()).doesNotContain("社外秘");
        Map<String, Object> pick = refs.get("pick");
        assertThat(pick).containsEntry("kind", "LOOKUP").containsEntry("status", "AVAILABLE");
        Map<String, Object> picker = (Map<String, Object>) pick.get("picker");
        assertThat((List<Map<String, Object>>) picker.get("searchFields"))
                .singleElement()
                .satisfies(f -> assertThat(f)
                        .containsEntry("column", "l")
                        .containsEntry("operator", "CONTAINS")
                        .containsEntry("valueType", "STRING"));
        assertThat((List<Map<String, Object>>) picker.get("listColumns"))
                .extracting(f -> f.get("column"))
                .containsExactly("l", "v");
        assertThat(picker.toString()).doesNotContain("r10000");
        Map<String, Object> pickColumn = ((List<Map<String, Object>>) definition.get("detailColumns"))
                .stream()
                        .filter(c -> c.get("column").equals("pick"))
                        .findFirst()
                        .orElseThrow();
        assertThat(pickColumn).containsEntry("formPart", "lookup");
    }

    @Test
    @DisplayName(
            "without a purpose the definition is VIEW: every reference is null and the form part stays text (T-C1)")
    void viewIsUnchanged() {
        Map<String, Object> none = definition(SRC_MISC, null);
        Map<String, Object> view = definition(SRC_MISC, "VIEW");

        assertThat(none).isEqualTo(view);
        assertThat(references(none).values()).allSatisfy(r -> assertThat(r).isNull());
        assertThat((List<Map<String, Object>>) none.get("detailColumns"))
                .filteredOn(c -> c.get("column").equals("pick"))
                .singleElement()
                .satisfies(c -> assertThat(c).containsEntry("formPart", "text"));
    }

    @Test
    @DisplayName("an unknown purpose is 400 VALIDATION_FAILED")
    void unknownPurpose() {
        HttpResponse<String> response = definitionResponse(SRC_MISC, "DELETE");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(HttpTestClient.json(response)).containsEntry("code", "VALIDATION_FAILED");
    }

    // ── B2: 検索・ラベルの1件読み・確かめの口 ──

    private HttpResponse<String> rowsResponse(Map<String, Object> body) {
        return app.post(BusinessDataPaths.REFERENCE_ROWS, member.token(), body);
    }

    private List<Map<String, Object>> pageRows(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("rows");
    }

    private Map<String, Object> search(String column, String conditionColumn, String value, int page) {
        HttpResponse<String> response = rowsResponse(sample.body(
                SRC_PICK,
                "column",
                column,
                "conditions",
                List.of(Map.of("column", conditionColumn, "value", value)),
                "page",
                page));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return HttpTestClient.json(response);
    }

    private HttpResponse<String> labelResponse(String table, String column, String value) {
        return rowsResponse(sample.body(table, "column", column, "value", value));
    }

    private Object label(String table, String column, String value) {
        HttpResponse<String> response = labelResponse(table, column, value);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Map<String, Object> json = HttpTestClient.json(response);
        assertThat(json).containsEntry("page", null).containsEntry("hasNext", false);
        return pageRows(json).get(0).get("label");
    }

    /** アプリの部品で写しを取り、REFERENCE の目的で借りた接続の中で確かめの口を呼ぶ（U2 と同じ呼び方）。 */
    private <T> T withReference(String table, BiFunction<TableAccessResolution.Resolved, TargetReadSession, T> work) {
        TableAccessResolution.Resolved source = (TableAccessResolution.Resolved) app.bean(TableAccessResolver.class)
                .resolve(member.userId(), new TableQuery(sample.schema(), table, null));
        TargetReadOutcome<T> outcome = app.bean(TargetReadAccess.class)
                .withRead(ReadPurpose.REFERENCE, session -> work.apply(source, session));
        assertThat(outcome).isInstanceOf(TargetReadOutcome.Done.class);
        return ((TargetReadOutcome.Done<T>) outcome).value();
    }

    private ReferenceCheckResult check(String table, String column, String value) {
        ReferenceLookup lookup = app.bean(ReferenceLookup.class);
        return withReference(
                table, (source, session) -> lookup.check(source, column, new ReferenceValue(value), session));
    }

    private ReferenceCountState countState(String table, String column) {
        ReferenceLookup lookup = app.bean(ReferenceLookup.class);
        return withReference(table, (source, session) -> lookup.countState(source, column, session));
    }

    @Test
    @DisplayName(
            "the picker returns 20 rows a page and hasNext only when a 21st row exists, long values left out (T-B5)")
    void pickerPages() {
        Map<String, Object> a20 = search("pick", "l", "A20-", 1);
        assertThat(pageRows(a20)).hasSize(20);
        assertThat(a20).containsEntry("hasNext", false).containsEntry("page", 1);
        assertThat(pageRows(a20).get(0))
                .containsEntry("value", "PA2000001")
                .containsEntry("label", "A20-00001")
                .containsEntry("cells", List.of("A20-00001", "PA2000001"));

        assertThat(search("pick", "l", "B21-", 1)).containsEntry("hasNext", true);
        assertThat(pageRows(search("pick", "l", "B21-", 2))).singleElement();
        assertThat(search("pick", "l", "C40-", 2)).containsEntry("hasNext", false);
        assertThat(pageRows(search("pick", "l", "C40-", 2))).hasSize(20);
        assertThat(search("pick", "l", "D41-", 2)).containsEntry("hasNext", true);
        Map<String, Object> last = search("pick", "l", "D41-", 3);
        assertThat(pageRows(last)).singleElement();
        assertThat(last).containsEntry("hasNext", false);
        Map<String, Object> after = search("pick", "l", "D41-", 4);
        assertThat(pageRows(after)).isEmpty();
        assertThat(after).containsEntry("hasNext", false);
        for (String group : List.of("A20-", "B21-", "C40-", "D41-")) {
            for (int page = 1; page <= 3; page++) {
                assertThat(pageRows(search("pick", "l", group, page)))
                        .as("10,000 字を超える値の行は入らない")
                        .allSatisfy(row -> assertThat((String) row.get("value")).startsWith("P"));
            }
        }
    }

    @Test
    @DisplayName("%, _ and the backslash in a partial match are taken as characters (e)")
    void containsEscapes() {
        assertThat(pageRows(search("esc", "l", "50%", 1)))
                .extracting(r -> r.get("value"))
                .containsExactly("e1");
        assertThat(pageRows(search("esc", "l", "%", 1)))
                .extracting(r -> r.get("value"))
                .containsExactly("e1");
        assertThat(pageRows(search("esc", "l", "a_b", 1)))
                .extracting(r -> r.get("value"))
                .containsExactly("e3");
        assertThat(pageRows(search("esc", "l", "c\\d", 1)))
                .extracting(r -> r.get("value"))
                .containsExactly("e5");
    }

    @Test
    @DisplayName("an EQUALS value is read as the column type: big integers and integers match, a non number is 400")
    void equalsTypes() {
        assertThat(pageRows(search("numpick", "v", ReferenceTables.BIG_MAX, 1)))
                .extracting(r -> r.get("label"))
                .containsExactly("max");
        assertThat(pageRows(search("numpick", "v", "0001", 1)))
                .extracting(r -> r.get("label"))
                .containsExactly("one");
        assertThat(pageRows(search("intpick", "id", "5", 1)))
                .singleElement()
                .satisfies(r ->
                        assertThat(r).containsEntry("value", "V00005").containsEntry("cells", List.of("L00005", "5")));
        for (Object[] bad : new Object[][] {{"numpick", "v", "abc"}, {"intpick", "id", "1.5"}}) {
            HttpResponse<String> response = rowsResponse(sample.body(
                    SRC_PICK,
                    "column",
                    bad[0],
                    "conditions",
                    List.of(Map.of("column", bad[1], "value", bad[2])),
                    "page",
                    1));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(HttpTestClient.json(response))
                    .containsEntry("code", "VALIDATION_FAILED")
                    .containsEntry("fieldErrors", List.of(Map.of("field", bad[1], "reason", "INVALID_VALUE")));
        }
    }

    @Test
    @DisplayName("the label read follows the database comparison: a case-insensitive collation may return another case")
    void labelFollowsTheCollation() {
        Object lower = label(SRC_SHAPES, "dup", "k");
        if (product() == DatabaseProduct.POSTGRESQL) {
            assertThat(lower).as("PostgreSQL は大文字と小文字を区別する").isEqualTo("c");
        } else {
            assertThat(lower).as("MySQL・MariaDB の既定の照合は区別しない（並びの最初の行のラベル）").isEqualTo("a");
        }
        assertThat(label(SRC_SHAPES, "dup", "K")).isEqualTo("a");
        HttpResponse<String> missing = labelResponse(SRC_SHAPES, "dup", "zz");
        assertThat(missing.statusCode()).as("行が無ければ 403").isEqualTo(403);
        assertThat(HttpTestClient.json(missing)).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    @DisplayName(
            "labels are read for composite keys, tables without keys, views, self references and odd values (h, f)")
    void labelShapes() {
        assertThat(label(SRC_SHAPES, "comp", "B1")).isEqualTo("one");
        assertThat(label(SRC_SHAPES, "nokey", "N1")).isEqualTo("n");
        assertThat(label(SRC_SHAPES, "view", "V00001")).isEqualTo("L00001");
        assertThat(label(SRC_SHAPES, "parent", "1")).isEqualTo("親");
        assertThat(label(SRC_SHAPES, "num", ReferenceTables.BIG_MAX)).isEqualTo("max");
        assertThat(label(SRC_SHAPES, "dec", "1.500")).isEqualTo("x");
        assertThat(label(SRC_SHAPES, "quoted", "it's")).isEqualTo("say \"hi\"");
        assertThat(label(SRC_MISC, "xss", ReferenceTables.XSS_VALUE)).isEqualTo(ReferenceTables.XSS_LABEL);
        assertThat(labelResponse(SRC_SHAPES, "num", "abc").statusCode())
                .as("型として読めない値は 403")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("check matches strictly: case, trailing spaces and long values differ, 1.0 equals 1 on every database")
    void checkIsStrict() {
        assertThat(check(SRC_SHAPES, "dup", "k")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_SHAPES, "dup", "K")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_SHAPES, "dup", "K ")).isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(check(SRC_PICK, "csref", "abc")).isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(check(SRC_PICK, "csref", "ABC")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_SHAPES, "dec", "1.500")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_SHAPES, "dec", "1.6")).isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(check(SRC_SHAPES, "num", "abc")).isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
    }

    @Test
    @DisplayName("check on trailing spaces gives the same answer on every database, including MariaDB PAD SPACE (T-B7)")
    void checkPadSpace() {
        assertThat(check(SRC_PICK, "padref", "x")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_PICK, "padref", "x" + " ".repeat(9_999))).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_PICK, "padref", "x" + " ".repeat(10_000)))
                .as("10,001 字の行は長さの条件で外れる")
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(check(SRC_PICK, "padref", "x ")).isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
    }

    @Test
    @DisplayName("check finds values of 10,000 characters and not of 10,001, and whole values over 100 (T-B6, BR7.3)")
    void checkLengths() {
        assertThat(check(SRC_LENGTHS, "edge", "a".repeat(10_000))).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_LENGTHS, "edge", "あ".repeat(10_000))).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_LENGTHS, "edge", SUPPLEMENTARY.repeat(10_000)))
                .isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_LENGTHS, "edge", "a".repeat(10_001)))
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(check(SRC_LENGTHS, "edge", SUPPLEMENTARY.repeat(10_001)))
                .isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
        assertThat(check(SRC_LENGTHS, "edge", "c".repeat(101))).isEqualTo(new ReferenceCheckResult.Found());
    }

    @Test
    @DisplayName("check reads composite keys, tables without keys, views and self references (h)")
    void checkShapes() {
        assertThat(check(SRC_SHAPES, "comp", "B2")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_SHAPES, "nokey", "N2")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_SHAPES, "view", "V00199")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_SHAPES, "parent", "2")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(check(SRC_MISC, "secret", "S1")).isEqualTo(new ReferenceCheckResult.NotFoundOrDenied());
    }

    @Test
    @DisplayName("countState is AVAILABLE at 200 and TOO_MANY at 201 with long values mixed in (T-B4)")
    void countStates() {
        assertThat(countState(SRC_LENGTHS, "m200")).isEqualTo(new ReferenceCountState.Available());
        assertThat(countState(SRC_LENGTHS, "m201")).isEqualTo(new ReferenceCountState.TooMany());
        assertThat(countState(SRC_COUNTS, "c199")).isEqualTo(new ReferenceCountState.Available());
        assertThat(countState(SRC_COUNTS, "c10000")).isEqualTo(new ReferenceCountState.TooMany());
        assertThat(countState(SRC_MISC, "secret")).isEqualTo(new ReferenceCountState.NoPermission());
        assertThat(countState(SRC_PICK, "pick")).isEqualTo(new ReferenceCountState.Available());
    }

    @Test
    @DisplayName("2,000 rows with 200 long labels work for the picker, the label, check and countState (T-B9)")
    void longLabelsOnEveryPath() {
        Map<String, Object> page = search("widepick", "l", "長", 1);
        assertThat(pageRows(page)).hasSize(20);
        assertThat(page).containsEntry("hasNext", true);
        assertThat(pageRows(page))
                .allSatisfy(row -> assertThat(row.get("label")).isEqualTo("長".repeat(100)));
        assertThat(label(SRC_MISC, "wide", "W00010")).isEqualTo("長".repeat(100));
        assertThat(check(SRC_MISC, "wide", "W00010")).isEqualTo(new ReferenceCheckResult.Found());
        assertThat(countState(SRC_MISC, "wide")).isEqualTo(new ReferenceCountState.TooMany());
    }
}
