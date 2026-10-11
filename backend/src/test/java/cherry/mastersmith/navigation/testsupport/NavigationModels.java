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

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * navigation のテストで使う版 2 の DSL のモデルとメニューの項目を組み立てる（Spring を起動しない単体テストと結合テストの両方で使う）。
 * テーブル・スキーマの定義は role の {@link RoleDslFixture} の形を使い、メニューを足したモデルを作る。モデルの識別は呼ぶたびに変える
 * （差し替えの前後を見分けるため）。
 */
public final class NavigationModels {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private NavigationModels() {}

    /**
     * モデルを作る。
     *
     * @param schemas スキーマ（この順）
     * @param menus 最上位のメニュー（この順）
     * @return モデル
     */
    public static DslModel model(List<DslSchema> schemas, List<DslMenuItem> menus) {
        return new DslModel(nextHash(), 2, schemas, menus);
    }

    /**
     * スキーマを1つ作る（テーブルごとにカラム {@code ID} を1つ持つ）。
     *
     * @param name スキーマの名前
     * @param tableNames テーブルの名前（この順）
     * @return スキーマ
     */
    public static DslSchema schema(String name, String... tableNames) {
        return RoleDslFixture.schema(
                name,
                java.util.Arrays.stream(tableNames)
                        .map(table -> RoleDslFixture.table(table, "ID"))
                        .toArray(cherry.mastersmith.dsl.domain.DslTable[]::new));
    }

    /**
     * テーブルを指す項目を作る（アイコンは無し）。
     *
     * @param label 表示名（日本語。英語は「-en」を付けた値）
     * @param schema スキーマの名前
     * @param table テーブルの名前
     * @param children 子
     * @return 項目
     */
    public static DslMenuItem tableItem(String label, String schema, String table, DslMenuItem... children) {
        return new DslMenuItem(
                new DisplayName(label, label + "-en"), null, new TableRef(schema, table), List.of(children));
    }

    /**
     * まとまり（テーブルを持たない項目）を作る。
     *
     * @param label 表示名（日本語。英語は「-en」を付けた値）
     * @param children 子（1つ以上）
     * @return 項目
     */
    public static DslMenuItem group(String label, DslMenuItem... children) {
        return new DslMenuItem(new DisplayName(label, label + "-en"), "home", null, List.of(children));
    }

    /**
     * 同じテーブルの組を順に回して指す、最上位だけのメニューを作る。
     *
     * @param count 項目の数
     * @param schema スキーマの名前
     * @param tables テーブルの名前（順に回す）
     * @return 最上位のメニュー
     */
    public static List<DslMenuItem> flatMenus(int count, String schema, String... tables) {
        List<DslMenuItem> menus = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            menus.add(tableItem("項目" + i, schema, tables[i % tables.length]));
        }
        return menus;
    }

    private static String nextHash() {
        return HexFormat.of().formatHex(new byte[24]) + String.format("%016x", SEQUENCE.incrementAndGet());
    }
}
