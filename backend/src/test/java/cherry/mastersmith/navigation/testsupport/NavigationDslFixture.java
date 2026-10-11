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
package cherry.mastersmith.navigation.testsupport;

import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.MainPermission;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * navigation の結合テストで使う版 2 の DSL を {@link ActiveDslModelHolder} に置く・外す（role の {@code RoleDslFixture} と同じ置き方、
 * 計画の 4.4・D-9）。モデルはメモリで組み立てて直に置くため、DSL の検証が拒みうる名前（制御文字など）も置ける。
 *
 * <p>悪い側の組（100 テーブル × 100 カラム・menus 1,000 項目・深さ 5）の明示の設定の行は、{@link #insertTableSettings} で SQL で
 * まとめて入れる。
 */
public final class NavigationDslFixture {

    private NavigationDslFixture() {}

    /**
     * モデルを適用中にする。
     *
     * @param holder 適用中のモデルの差し替えの口
     * @param schemas スキーマ（この順）
     * @param menus 最上位のメニュー（この順）
     * @return 置いたモデル
     */
    public static DslModel install(ActiveDslModelHolder holder, List<DslSchema> schemas, List<DslMenuItem> menus) {
        DslModel model = NavigationModels.model(schemas, menus);
        holder.replace(model);
        return model;
    }

    /**
     * 適用中の DSL を外す（適用済みの DSL が無い状態にする）。
     *
     * @param holder 適用中のモデルの差し替えの口
     */
    public static void remove(ActiveDslModelHolder holder) {
        holder.replace(null);
    }

    /**
     * 悪い側のメニュー（1,000 項目・深さ 5）を作る。最上位のまとまり 40 個のそれぞれに、4 段のまとまりの鎖と、鎖の各段に表の項目を
     * 置く。各まとまりの最も深い段（5 段目）は表を指す。表は {@code tables} を順に回して指す。
     *
     * @param schema スキーマの名前
     * @param tables テーブルの名前（順に回す）
     * @return 最上位のメニュー（項目の総数 1,000）
     */
    public static List<DslMenuItem> worstMenus(String schema, List<String> tables) {
        int[] next = {0};
        List<DslMenuItem> roots = new ArrayList<>();
        for (int g = 0; g < 40; g++) {
            // 1 つのまとまりの木は 25 項目: 段 1〜4 のまとまり（4）、段 2〜5 に表の項目を 5 個ずつ（20）、段 5 の鎖の先の表（1）。
            DslMenuItem level5 = NavigationModels.tableItem("深い" + g, schema, table(tables, next));
            DslMenuItem chain = level5;
            for (int level = 4; level >= 1; level--) {
                List<DslMenuItem> children = new ArrayList<>();
                children.add(chain);
                for (int k = 0; k < 5; k++) {
                    children.add(
                            NavigationModels.tableItem("表" + g + "-" + level + "-" + k, schema, table(tables, next)));
                }
                chain = NavigationModels.group("まとまり" + g + "-" + level, children.toArray(DslMenuItem[]::new));
            }
            roots.add(chain);
        }
        return roots;
    }

    private static String table(List<String> tables, int[] next) {
        return tables.get(next[0]++ % tables.size());
    }

    /**
     * ロールのテーブルの階層の明示の主権限を、SQL でまとめて入れる。
     *
     * @param jdbc 内部DB への SQL の部品
     * @param roleId ロール ID
     * @param schema スキーマの名前
     * @param mains テーブルの名前ごとの主権限
     */
    public static void insertTableSettings(
            JdbcTemplate jdbc, long roleId, String schema, Map<String, MainPermission> mains) {
        insert(jdbc, roleId, schema, mains, null);
    }

    /**
     * ロールのカラムの階層の明示の主権限を、SQL でまとめて入れる（各テーブルのカラムに同じ値）。
     *
     * @param jdbc 内部DB への SQL の部品
     * @param roleId ロール ID
     * @param schema スキーマの名前
     * @param columnsByTable テーブルの名前ごとのカラムの名前と主権限
     */
    public static void insertColumnSettings(
            JdbcTemplate jdbc, long roleId, String schema, Map<String, Map<String, MainPermission>> columnsByTable) {
        List<Object[]> rows = new ArrayList<>();
        OffsetDateTime at = OffsetDateTime.of(2026, 10, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        columnsByTable.forEach((table, columns) -> columns.forEach(
                (column, main) -> rows.add(new Object[] {roleId, schema, table, column, main.name(), at})));
        batch(jdbc, rows);
    }

    private static void insert(
            JdbcTemplate jdbc, long roleId, String schema, Map<String, MainPermission> mains, String column) {
        List<Object[]> rows = new ArrayList<>();
        OffsetDateTime at = OffsetDateTime.of(2026, 10, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        mains.forEach((table, main) ->
                rows.add(new Object[] {roleId, schema, table, column == null ? "" : column, main.name(), at}));
        batch(jdbc, rows);
    }

    private static void batch(JdbcTemplate jdbc, List<Object[]> rows) {
        jdbc.batchUpdate(
                "INSERT INTO permission_settings (role_id, schema_name, table_name, column_name, main_permission,"
                        + " create_permission, delete_permission, updated_at) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)",
                rows,
                500,
                (PreparedStatement statement, Object[] row) -> {
                    statement.setLong(1, (Long) row[0]);
                    statement.setString(2, (String) row[1]);
                    statement.setString(3, (String) row[2]);
                    statement.setString(4, (String) row[3]);
                    statement.setString(5, (String) row[4]);
                    statement.setObject(6, row[5]);
                });
    }
}
