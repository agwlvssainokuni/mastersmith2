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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.table;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.common.testsupport.HttpTestClient;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * 長い文字のカラムで並べ替えた一覧の深いページの結合テスト（Intent 261101-maintenance-update の P1、FR9.1、K-92）。子のクラスで DB の
 * 種類を決める。
 *
 * <p>MySQL 26.10.0（{@code sort_buffer_size} 既定の 262,144・照合順序 {@code utf8mb4_0900_ai_ci}）では、並べ替えのカラムの値1つが
 * 大きいと（漢字でおよそ 8,000 字を超えると）、{@code OFFSET} と件数に依らず 1038（並べ替えの領域の不足）になった。一覧の並びを
 * 参照先の並びと同じ先頭 {@code LIST_TEXT_LIMIT} 字の式に揃えて直した（依頼者の決定 案 A）。ここでは、アプリが書ける上限の 10,000 字の
 * 漢字の値を 1,000 行置き、1ページの件数の上限 100 で先頭と最後のページ（{@code OFFSET} 900）を読む。値はすべて架空。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
abstract class AbstractDeepPageSortIT {

    /** 表の名前。 */
    private static final String TABLE = "long_text";

    /** 行の数。 */
    private static final int ROWS = 1_000;

    /** 値の文字数（アプリが書ける上限。ColumnWriteRulesFactory.MAX_VALUE_LENGTH）。 */
    private static final int LENGTH = 10_000;

    /** 1ページの件数の上限。 */
    private static final int SIZE = 100;

    /** 最後のページ（OFFSET 900）。 */
    private static final int LAST_PAGE = ROWS / SIZE;

    /** 並べ替えの式が切る長さを超えて、先頭が同じ値になる部分（100 字）。 */
    private static final String SHARED_HEAD = "共".repeat(100);

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

    /** 行の番号（1〜ROWS）から、並びを決める番号（0〜ROWS-1 の並べ替え）を作る。 */
    private static int rank(int id) {
        return (id * 7919) % ROWS;
    }

    /** 先頭5字の番号の後を漢字で埋めた、10,000 字の値。 */
    private static String body(int id) {
        String head = String.format("%05d", rank(id));
        return head + "名".repeat(LENGTH - head.length());
    }

    /** 先頭 100 字が全行で同じで、101 字目以降で違う、10,000 字の値。 */
    private static String shared(int id) {
        String head = SHARED_HEAD + String.format("%05d", rank(id));
        return head + "名".repeat(LENGTH - head.length());
    }

    @BeforeAll
    void start(@TempDir Path dir) throws SQLException {
        database.start();
        database.createSchema(schema);
        String t = database.q(schema) + "." + database.q(TABLE);
        database.execute("CREATE TABLE " + t + " (id INTEGER NOT NULL PRIMARY KEY, body "
                + (postgres() ? "TEXT" : "LONGTEXT") + " NULL, same VARCHAR(10000) NULL)"
                + (postgres() ? "" : " CHARACTER SET utf8mb4"));
        try (Connection connection = database.admin();
                PreparedStatement insert =
                        connection.prepareStatement("INSERT INTO " + t + " (id, body, same) VALUES (?, ?, ?)")) {
            for (int id = 1; id <= ROWS; id++) {
                insert.setInt(1, id);
                insert.setString(2, body(id));
                insert.setString(3, shared(id));
                insert.executeUpdate();
            }
        }
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
        app.installDsl(BusinessDataDslFixture.model(
                schema,
                table(
                        TABLE,
                        false,
                        List.of("id"),
                        column("id")
                                .dbType(postgres() ? "int4" : "int")
                                .list(true, 1)
                                .build(),
                        column("body")
                                .dbType(postgres() ? "text" : "longtext")
                                .list(true, 2)
                                .build(),
                        column("same").list(true, 3).build())));
        Map<PermissionTarget, PermissionValues> settings = new LinkedHashMap<>();
        settings.put(PermissionTarget.schema(schema), new PermissionValues(MainPermission.READ, false, false));
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

    private HttpResponse<String> searchResponse(String column, String direction, int page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", schema);
        body.put("table", TABLE);
        body.put("conditions", List.of());
        body.put("page", page);
        body.put("size", SIZE);
        body.put("sort", Map.of("column", column, "direction", direction));
        return app.post(BusinessDataPaths.SEARCH, member.token(), body);
    }

    @SuppressWarnings("unchecked")
    private List<Integer> ids(String column, String direction, int page) {
        HttpResponse<String> response = searchResponse(column, direction, page);
        // 失敗の応答の本文は Problem Details で、行の値を含まない。
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Map<String, Object> json = HttpTestClient.json(response);
        assertThat(json).containsEntry("total", ROWS);
        return ((List<Map<String, Object>>) json.get("items"))
                .stream()
                        // 応答の値は型によらず文字で返る。
                        .map(row ->
                                Integer.valueOf(String.valueOf(((Map<String, Object>) row.get("values")).get("id"))))
                        .toList();
    }

    /** 並びを決める番号の順に並べた行の番号のうち、指定のページの分。 */
    private static List<Integer> expected(Comparator<Integer> order, int page) {
        return IntStream.rangeClosed(1, ROWS)
                .boxed()
                .sorted(order)
                .skip((long) (page - 1) * SIZE)
                .limit(SIZE)
                .toList();
    }

    @Test
    @DisplayName("a list sorted by a 10,000-character column reads the first and the deepest page of 100 rows in order")
    void sortsLongTextAscending() {
        Comparator<Integer> byRank = Comparator.comparingInt(AbstractDeepPageSortIT::rank);

        assertThat(ids("body", "ASC", 1)).containsExactlyElementsOf(expected(byRank, 1));
        assertThat(ids("body", "ASC", LAST_PAGE)).containsExactlyElementsOf(expected(byRank, LAST_PAGE));
    }

    @Test
    @DisplayName("a list sorted descending by a 10,000-character column reads the deepest page of 100 rows in order")
    void sortsLongTextDescending() {
        Comparator<Integer> byRankDescending =
                Comparator.comparingInt(AbstractDeepPageSortIT::rank).reversed();

        assertThat(ids("body", "DESC", LAST_PAGE)).containsExactlyElementsOf(expected(byRankDescending, LAST_PAGE));
    }

    @Test
    @DisplayName(
            "values sharing the first 100 characters are ordered by the primary key, without overlap between pages")
    void sharedHeadFallsBackToPrimaryKey() {
        // 並べ替えの式は先頭 100 字だけを比べるため、先頭が同じ値どうしは主キーの昇順で決まる（案 A の決めた動作）。
        Comparator<Integer> byId = Comparator.naturalOrder();

        assertThat(ids("same", "ASC", 1)).containsExactlyElementsOf(expected(byId, 1));
        assertThat(ids("same", "ASC", LAST_PAGE)).containsExactlyElementsOf(expected(byId, LAST_PAGE));
    }
}
