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
package cherry.mastersmith.role.testsupport;

import cherry.mastersmith.dsl.domain.DbType;
import cherry.mastersmith.dsl.domain.DetailSetting;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ListSetting;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SearchSetting;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * role のテストで使う版 2 の DSL のモデル（計画の 7.3、{@code unit-test-instructions.md} 7節）。小さな DSL と、名前に記号を含む
 * DSL を持ち、{@link ActiveDslModelHolder} に置く・外す。
 *
 * <p>小さな DSL: スキーマ {@code SALES}（テーブル {@code ORDER_LINE}（カラム {@code ORDER_NO}・{@code UNIT_PRICE}・{@code QTY}）・
 * {@code ORDER_HEAD}（カラム {@code ID}））と {@code HR}（テーブル {@code EMPLOYEE}（カラム {@code ID}・{@code NAME}））。並びはこの順。
 */
public final class RoleDslFixture {

    /** テストの DSL の識別（16 進数の小文字 64 文字）。 */
    public static final String HASH = "0123456789abcdef".repeat(4);

    /** 名前に記号を含むスキーマ（道や問い合わせの区切りに当たる文字）。 */
    public static final String SYMBOL_SCHEMA = "a/b..c;d%e f";

    /** 名前に記号を含むテーブル。 */
    public static final String SYMBOL_TABLE = "t/1;%..x y";

    /** 名前が値の書き方に似たカラム。 */
    public static final List<String> SYMBOL_COLUMNS = List.of("true", "null", "123");

    private RoleDslFixture() {}

    /**
     * カラムの定義を作る（表示名は日本語と英語で名前を含む）。
     *
     * @param name 名前
     * @return カラム
     */
    public static DslColumn column(String name) {
        return new DslColumn(
                name,
                new DisplayName("カラム " + name, "Column " + name),
                new DbType("VARCHAR", 10, null, null, false),
                FormPart.TEXT,
                new SearchSetting(true, SearchOperator.EQUALS, null, false),
                new ListSetting(true, 1, null, true, null, null),
                new DetailSetting(true),
                List.of(),
                null);
    }

    /**
     * テーブルの定義を作る。
     *
     * @param name 名前
     * @param columnNames カラムの名前（1つ以上、この順）
     * @return テーブル
     */
    public static DslTable table(String name, String... columnNames) {
        Map<String, DslColumn> columns = new LinkedHashMap<>();
        for (String columnName : columnNames) {
            columns.put(columnName, column(columnName));
        }
        return new DslTable(
                name,
                new DisplayName("テーブル " + name, "Table " + name),
                false,
                List.of(columnNames[0]),
                List.of(),
                columns);
    }

    /**
     * スキーマの定義を作る。
     *
     * @param name 名前
     * @param tables テーブル（この順）
     * @return スキーマ
     */
    public static DslSchema schema(String name, DslTable... tables) {
        Map<String, DslTable> map = new LinkedHashMap<>();
        for (DslTable table : tables) {
            map.put(table.name(), table);
        }
        return new DslSchema(name, new DisplayName("スキーマ " + name, "Schema " + name), map);
    }

    /**
     * スキーマからモデルを作る。
     *
     * @param schemas スキーマ（この順）
     * @return モデル
     */
    public static DslModel model(DslSchema... schemas) {
        return new DslModel(HASH, 2, List.of(schemas), List.of());
    }

    /**
     * 小さな DSL のモデルを返す。
     *
     * @return モデル
     */
    public static DslModel sample() {
        return model(
                schema("SALES", table("ORDER_LINE", "ORDER_NO", "UNIT_PRICE", "QTY"), table("ORDER_HEAD", "ID")),
                schema("HR", table("EMPLOYEE", "ID", "NAME")));
    }

    /**
     * 名前に記号を含む DSL のモデルを返す（小さな DSL のスキーマも持つ）。
     *
     * @return モデル
     */
    public static DslModel withSymbols() {
        return model(
                schema("SALES", table("ORDER_LINE", "ORDER_NO", "UNIT_PRICE", "QTY"), table("ORDER_HEAD", "ID")),
                schema(SYMBOL_SCHEMA, table(SYMBOL_TABLE, SYMBOL_COLUMNS.toArray(String[]::new))));
    }

    /**
     * モデルを適用中にする。
     *
     * @param holder 適用中のモデルの差し替えの口
     * @param model モデル
     */
    public static void install(ActiveDslModelHolder holder, DslModel model) {
        holder.replace(model);
    }

    /**
     * 適用中の DSL を外す（適用済みの DSL が無い状態にする）。
     *
     * @param holder 適用中のモデルの差し替えの口
     */
    public static void remove(ActiveDslModelHolder holder) {
        holder.replace(null);
    }
}
