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
package cherry.mastersmith.businessdata.testsupport;

import cherry.mastersmith.businessdata.domain.BoundValue;
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.ColumnKind;
import cherry.mastersmith.businessdata.domain.ColumnValueReader;
import cherry.mastersmith.businessdata.domain.DefinitionId;
import cherry.mastersmith.businessdata.domain.KnownIdentifiers;
import cherry.mastersmith.businessdata.domain.TableAccessInput;
import cherry.mastersmith.businessdata.domain.TableAccessPolicy;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.DbType;
import cherry.mastersmith.dsl.domain.DetailSetting;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslForeignKey;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.ListFormat;
import cherry.mastersmith.dsl.domain.ListSetting;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.SearchOperator;
import cherry.mastersmith.dsl.domain.SearchSetting;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.dsl.domain.Validation;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import cherry.mastersmith.dsl.domain.ValidationType;
import cherry.mastersmith.role.domain.MainPermission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * businessdata のテストで使う DSL の定義と判定の材料（unit-test-instructions.md の 7節）。値はすべて架空。
 */
public final class BusinessDataDslFixture {

    /** テストの DSL の識別（16 進数の小文字 64 文字）。 */
    public static final String HASH = "0123456789abcdef".repeat(4);

    /** 別の DSL の識別。 */
    public static final String OTHER_HASH = "fedcba9876543210".repeat(4);

    /** テストの定義の識別（作業ロール 7）。 */
    public static final DefinitionId DEFINITION = new DefinitionId(HASH, 7L);

    private BusinessDataDslFixture() {}

    /**
     * カラムの定義を組み立てる（既定は VARCHAR(100)・text・検索なし・一覧と詳細に出す・並べ替えできる）。
     *
     * @param name カラムの名前
     * @return 組み立て
     */
    public static ColumnBuilder column(String name) {
        return new ColumnBuilder(name);
    }

    /**
     * テーブルの定義を作る。
     *
     * @param name テーブルの名前
     * @param view ビューなら true
     * @param primaryKey 主キーのカラムの名前
     * @param columns カラム（この順）
     * @return テーブル
     */
    public static DslTable table(String name, boolean view, List<String> primaryKey, DslColumn... columns) {
        Map<String, DslColumn> map = new LinkedHashMap<>();
        for (DslColumn column : columns) {
            map.put(column.name(), column);
        }
        return new DslTable(name, new DisplayName("表 " + name, "Table " + name), view, primaryKey, List.of(), map);
    }

    /**
     * 外部キーのあるテーブルの定義を作る（U2）。
     *
     * @param name テーブルの名前
     * @param primaryKey 主キーのカラムの名前
     * @param foreignKeys 外部キー
     * @param columns カラム（この順）
     * @return テーブル
     */
    public static DslTable tableWithForeignKeys(
            String name, List<String> primaryKey, List<DslForeignKey> foreignKeys, DslColumn... columns) {
        Map<String, DslColumn> map = new LinkedHashMap<>();
        for (DslColumn column : columns) {
            map.put(column.name(), column);
        }
        return new DslTable(name, new DisplayName("表 " + name, "Table " + name), false, primaryKey, foreignKeys, map);
    }

    /**
     * 1つのスキーマの DSL のモデルを作る。
     *
     * @param schema スキーマの名前
     * @param tables テーブル
     * @return モデル
     */
    public static DslModel model(String schema, DslTable... tables) {
        Map<String, DslTable> map = new LinkedHashMap<>();
        for (DslTable table : tables) {
            map.put(table.name(), table);
        }
        return new DslModel(HASH, 2, List.of(new DslSchema(schema, new DisplayName("スキーマ", "Schema"), map)), List.of());
    }

    /**
     * 名前の一覧を作る（スキーマ1つ、テーブル1つ）。
     *
     * @param schema スキーマの名前
     * @param table テーブル
     * @return 名前の一覧
     */
    public static KnownIdentifiers identifiers(String schema, DslTable table) {
        return new KnownIdentifiers(
                Set.of(schema), Set.of(table.name()), table.columns().keySet());
    }

    /**
     * 判定の材料を作る（すべてのカラムに同じ主権限、受け渡しの型は {@code kinds} に無ければ STRING）。
     *
     * @param table テーブル
     * @param main テーブルとカラムの主権限
     * @param kinds カラムの受け渡しの型（無いものは STRING・完全一致に使える）
     * @return 判定の材料
     */
    public static TableAccessView view(DslTable table, MainPermission main, Map<String, ColumnKind> kinds) {
        Map<String, MainPermission> columnMain = new HashMap<>();
        table.columns().keySet().forEach(name -> columnMain.put(name, main));
        return TableAccessPolicy.evaluate(input(table, main, columnMain, kinds, true, true, false));
    }

    /**
     * 出し分けの関数の入力を作る。
     *
     * @param table テーブル
     * @param tableMain テーブルの主権限
     * @param columnMain カラムの主権限
     * @param kinds カラムの受け渡しの型（無いものは STRING・完全一致に使える）
     * @param canCreate 補助権限 CREATE
     * @param canDelete 補助権限 DELETE
     * @param writeConfigured 書き込みの設定があるか
     * @return 入力
     */
    public static TableAccessInput input(
            DslTable table,
            MainPermission tableMain,
            Map<String, MainPermission> columnMain,
            Map<String, ColumnKind> kinds,
            boolean canCreate,
            boolean canDelete,
            boolean writeConfigured) {
        Map<String, ColumnKind> all = new HashMap<>();
        table.columns().keySet().forEach(name -> all.put(name, kinds.getOrDefault(name, kind(ValueType.STRING))));
        return new TableAccessInput(
                "sample",
                table,
                DEFINITION,
                tableMain,
                canCreate,
                canDelete,
                columnMain,
                all,
                writeConfigured,
                identifiers("sample", table));
    }

    /**
     * 読み取りだけのカラムを1つ作る（型の部品のテスト用）。
     *
     * @param type 受け渡しの型
     * @param dbTypeName DSL の型の名前
     * @return カラム
     */
    public static ColumnAccess access(ValueType type, String dbTypeName) {
        return new ColumnAccess(
                "c",
                new DisplayName("c", "c"),
                MainPermission.READ,
                type,
                dbTypeName,
                false,
                true,
                1,
                null,
                null,
                true,
                null,
                null,
                false,
                false,
                null,
                false,
                false,
                false,
                null);
    }

    /**
     * 完全一致に使える受け渡しの型を作る。
     *
     * @param type 受け渡しの型
     * @return 型
     */
    public static ColumnKind kind(ValueType type) {
        return new ColumnKind(type, true);
    }

    /**
     * テスト用の型として読む口（数・日付・真偽値だけを読む小さな実装。本物は ColumnValueCodec）。
     *
     * @return 読む口
     */
    public static ColumnValueReader simpleReader() {
        return (column, text) -> {
            try {
                return Optional.of(
                        switch (column.valueType()) {
                            case INTEGER -> new BoundValue(ValueType.INTEGER, Long.valueOf(text));
                            case BIG_INTEGER, DECIMAL -> new BoundValue(column.valueType(), new BigDecimal(text));
                            case DATE -> new BoundValue(ValueType.DATE, LocalDate.parse(text));
                            case BOOLEAN -> {
                                if (!text.equals("true") && !text.equals("false")) {
                                    throw new IllegalArgumentException();
                                }
                                yield new BoundValue(ValueType.BOOLEAN, Boolean.valueOf(text));
                            }
                            default -> new BoundValue(column.valueType(), text);
                        });
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        };
    }

    /**
     * 固定の選択肢を作る。
     *
     * @param values 値
     * @return 選択肢の出どころ
     */
    public static OptionSource fixed(String... values) {
        List<OptionItem> items = Arrays.stream(values)
                .map(v -> new OptionItem(v, new DisplayName("選択 " + v, "Choice " + v)))
                .collect(Collectors.toList());
        return new OptionSource(OptionSourceKind.FIXED, items, null, null, null, List.of(), List.of());
    }

    /**
     * 参照の選択肢を作る。
     *
     * @return 選択肢の出どころ
     */
    public static OptionSource reference() {
        return new OptionSource(OptionSourceKind.REFERENCE, List.of(), "other", "id", "name", List.of(), List.of());
    }

    /** カラムの定義の組み立て。 */
    public static final class ColumnBuilder {

        private final String name;

        private String dbTypeName = "varchar";

        private Integer precision;

        private FormPart formPart = FormPart.TEXT;

        private SearchSetting search = new SearchSetting(false, null, null, false);

        private boolean listVisible = true;

        private Integer listOrder = 1;

        private Integer width;

        private boolean sortable = true;

        private SortDirection defaultSort;

        private ListFormat format;

        private boolean detailVisible = true;

        private OptionSource options;

        private Integer length;

        private Integer scale;

        private boolean nullable = true;

        private final List<Validation> validations = new ArrayList<>();

        private ColumnBuilder(String name) {
            this.name = name;
        }

        /**
         * DB の型の名前を決める。
         *
         * @param value 型の名前
         * @return この組み立て
         */
        public ColumnBuilder dbType(String value) {
            this.dbTypeName = value;
            return this;
        }

        /**
         * DB の型の名前と精度を決める。
         *
         * @param value 型の名前
         * @param precisionValue 精度
         * @return この組み立て
         */
        public ColumnBuilder dbType(String value, Integer precisionValue) {
            this.dbTypeName = value;
            this.precision = precisionValue;
            return this;
        }

        /**
         * フォーム部品を決める。
         *
         * @param value フォーム部品
         * @return この組み立て
         */
        public ColumnBuilder form(FormPart value) {
            this.formPart = value;
            return this;
        }

        /**
         * 検索の演算子を決める（検索に出す）。
         *
         * @param operator 演算子
         * @return この組み立て
         */
        public ColumnBuilder search(SearchOperator operator) {
            this.search = new SearchSetting(true, operator, null, false);
            return this;
        }

        /**
         * 検索の設定を決める。
         *
         * @param value 検索の設定
         * @return この組み立て
         */
        public ColumnBuilder search(SearchSetting value) {
            this.search = value;
            return this;
        }

        /**
         * 一覧に出すかと並び順を決める。
         *
         * @param visible 出すなら true
         * @param order 並び順
         * @return この組み立て
         */
        public ColumnBuilder list(boolean visible, Integer order) {
            this.listVisible = visible;
            this.listOrder = order;
            return this;
        }

        /**
         * 一覧の幅と書式を決める。
         *
         * @param widthValue 幅
         * @param formatValue 書式
         * @return この組み立て
         */
        public ColumnBuilder look(Integer widthValue, ListFormat formatValue) {
            this.width = widthValue;
            this.format = formatValue;
            return this;
        }

        /**
         * 並べ替えできるかを決める。
         *
         * @param value できるなら true
         * @return この組み立て
         */
        public ColumnBuilder sortable(boolean value) {
            this.sortable = value;
            return this;
        }

        /**
         * 既定の並びの向きを決める。
         *
         * @param value 向き
         * @return この組み立て
         */
        public ColumnBuilder defaultSort(SortDirection value) {
            this.defaultSort = value;
            return this;
        }

        /**
         * 詳細に出すかを決める。
         *
         * @param value 出すなら true
         * @return この組み立て
         */
        public ColumnBuilder detail(boolean value) {
            this.detailVisible = value;
            return this;
        }

        /**
         * 選択肢の出どころを決める。
         *
         * @param value 選択肢の出どころ
         * @return この組み立て
         */
        public ColumnBuilder options(OptionSource value) {
            this.options = value;
            return this;
        }

        /**
         * DB の型の長さを決める（U2）。
         *
         * @param value 長さ
         * @return この組み立て
         */
        public ColumnBuilder length(Integer value) {
            this.length = value;
            return this;
        }

        /**
         * DB の型の精度と小数の桁を決める（U2）。
         *
         * @param precisionValue 精度
         * @param scaleValue 小数の桁
         * @return この組み立て
         */
        public ColumnBuilder decimal(Integer precisionValue, Integer scaleValue) {
            this.precision = precisionValue;
            this.scale = scaleValue;
            return this;
        }

        /**
         * NULL を許すかを決める（U2。既定は許す）。
         *
         * @param value 許すなら true
         * @return この組み立て
         */
        public ColumnBuilder nullable(boolean value) {
            this.nullable = value;
            return this;
        }

        /**
         * 値を持たない検証（required・unique）を足す（U2）。
         *
         * @param type 種類
         * @param origin 出どころ
         * @return この組み立て
         */
        public ColumnBuilder validation(ValidationType type, ValidationOrigin origin) {
            validations.add(new Validation(type, null, null, origin, null));
            return this;
        }

        /**
         * 数の値を持つ検証（minLength・maxLength・min・max）を足す（U2）。
         *
         * @param type 種類
         * @param number 値
         * @return この組み立て
         */
        public ColumnBuilder validation(ValidationType type, String number) {
            validations.add(new Validation(type, new BigDecimal(number), null, ValidationOrigin.MANUAL, null));
            return this;
        }

        /**
         * 正規表現の検証を足す（U2）。
         *
         * @param regex 式
         * @param message 合わないときの文（無ければ null）
         * @return この組み立て
         */
        public ColumnBuilder pattern(String regex, DisplayName message) {
            validations.add(new Validation(ValidationType.PATTERN, null, regex, ValidationOrigin.MANUAL, message));
            return this;
        }

        /**
         * カラムの定義を作る。
         *
         * @return カラム
         */
        public DslColumn build() {
            return new DslColumn(
                    name,
                    new DisplayName("項目 " + name, "Item " + name),
                    new DbType(dbTypeName, length, precision, scale, nullable),
                    formPart,
                    search,
                    new ListSetting(
                            listVisible, listVisible ? listOrder : listOrder, width, sortable, defaultSort, format),
                    new DetailSetting(detailVisible),
                    validations,
                    options);
        }
    }
}
