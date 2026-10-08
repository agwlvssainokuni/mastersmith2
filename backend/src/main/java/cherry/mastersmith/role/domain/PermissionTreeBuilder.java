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
package cherry.mastersmith.role.domain;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 権限の設定の木の1階層を作る（{@code logical-components.md} の L6、BR4.10・BR4.11・BR5.3）。DB にも時計にも触れない純粋な関数である。
 *
 * <ul>
 *   <li>今の DSL の節と、設定だけがある対象（今の DSL に無い）を名前で合わせる。名前は大文字と小文字を区別して完全一致で照らす
 *   <li>並びは DSL の順、今の DSL に無いものはその後に名前のコードポイントの順
 *   <li>各節に明示・実効・継承の元（{@link PermissionInheritance}）・{@code inMenu}（テーブルの節だけ、実効の主権限が NONE でないとき）・
 *       {@code inCurrentDsl}・{@code hasChildren}（DSL の下の節か、下の階層の設定があるとき）を付ける
 *   <li>今の DSL に無い節の実効は NONE・不可で、継承の元は DEFAULT（今の DSL に無い対象の設定は使わない。BR5.3）
 * </ul>
 */
public final class PermissionTreeBuilder {

    /** 名前のコードポイントの順（UTF-16 の単位の順ではない）。 */
    public static final Comparator<String> CODE_POINT_ORDER = PermissionTreeBuilder::compareCodePoints;

    private PermissionTreeBuilder() {}

    /**
     * 設定の表から読んだ1つの子の分。
     *
     * @param explicit その子の階層の明示の値（行が無ければすべて設定なし）
     * @param deeperRows その子より下の階層の設定の行の数
     */
    public record StoredChild(PermissionValues explicit, long deeperRows) {

        /** 値が null でなく、数が負でないことを確かめる。 */
        public StoredChild {
            Objects.requireNonNull(explicit, "explicit");
            if (deeperRows < 0) {
                throw new IllegalArgumentException("数は 0 以上です");
            }
        }
    }

    private record DslChild(String name, DisplayName label, boolean hasChildren) {}

    /**
     * 1段目（スキーマ）の節を作る。
     *
     * @param dsl 適用中の DSL のモデル
     * @param stored スキーマの名前ごとの設定
     * @return 節の一覧
     */
    public static List<PermissionNode> schemas(DslModel dsl, Map<String, StoredChild> stored) {
        Objects.requireNonNull(dsl, "dsl");
        List<DslChild> dslChildren = dsl.schemas().stream()
                .map(schema -> new DslChild(
                        schema.name(), schema.label(), !schema.tables().isEmpty()))
                .toList();
        return build(
                dslChildren,
                stored,
                PermissionTarget::schema,
                (target, values) -> PermissionInheritance.resolve(PermissionLevel.SCHEMA, values, null, null));
    }

    /**
     * スキーマの下のテーブルの節を作る。
     *
     * @param dsl 適用中の DSL のモデル
     * @param schemaName スキーマの名前
     * @param schemaValues スキーマの明示の値（継承に使う）
     * @param stored テーブルの名前ごとの設定
     * @return 節の一覧
     */
    public static List<PermissionNode> tables(
            DslModel dsl, String schemaName, PermissionValues schemaValues, Map<String, StoredChild> stored) {
        Objects.requireNonNull(dsl, "dsl");
        List<DslChild> dslChildren = findSchema(dsl, schemaName)
                .map(schema -> schema.tables().values().stream()
                        .map(table -> new DslChild(
                                table.name(), table.label(), !table.columns().isEmpty()))
                        .toList())
                .orElse(List.of());
        return build(
                dslChildren,
                stored,
                name -> PermissionTarget.table(schemaName, name),
                (target, values) -> PermissionInheritance.resolve(PermissionLevel.TABLE, schemaValues, values, null));
    }

    /**
     * テーブルの下のカラムの節を作る。
     *
     * @param dsl 適用中の DSL のモデル
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @param schemaValues スキーマの明示の値（継承に使う）
     * @param tableValues テーブルの明示の値（継承に使う）
     * @param stored カラムの名前ごとの設定
     * @return 節の一覧
     */
    public static List<PermissionNode> columns(
            DslModel dsl,
            String schemaName,
            String tableName,
            PermissionValues schemaValues,
            PermissionValues tableValues,
            Map<String, StoredChild> stored) {
        Objects.requireNonNull(dsl, "dsl");
        List<DslChild> dslChildren = findSchema(dsl, schemaName)
                .map(schema -> schema.tables().get(tableName))
                .map(DslTable::columns)
                .map(columns -> columns.values().stream()
                        .map(column -> new DslChild(column.name(), column.label(), false))
                        .toList())
                .orElse(List.<DslChild>of());
        return build(
                dslChildren,
                stored,
                name -> PermissionTarget.column(schemaName, tableName, name),
                (target, values) ->
                        PermissionInheritance.resolve(PermissionLevel.COLUMN, schemaValues, tableValues, values));
    }

    /**
     * 名前のコードポイントの順で比べる。
     *
     * @param left 左
     * @param right 右
     * @return 比べた結果
     */
    static int compareCodePoints(String left, String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            int a = left.codePointAt(i);
            int b = right.codePointAt(j);
            if (a != b) {
                return Integer.compare(a, b);
            }
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        return Integer.compare(left.length() - i, right.length() - j);
    }

    private interface Resolver {
        PermissionInheritance.Resolution resolve(PermissionTarget target, PermissionValues values);
    }

    private static List<PermissionNode> build(
            List<DslChild> dslChildren,
            Map<String, StoredChild> stored,
            Function<String, PermissionTarget> targetOf,
            Resolver resolver) {
        Objects.requireNonNull(stored, "stored");
        List<PermissionNode> nodes = new ArrayList<>();
        Set<String> inDsl = new LinkedHashSet<>();
        for (DslChild child : dslChildren) {
            inDsl.add(child.name());
            StoredChild row = stored.get(child.name());
            PermissionValues explicit = row == null ? PermissionValues.NOT_SET : row.explicit();
            PermissionTarget target = targetOf.apply(child.name());
            PermissionInheritance.Resolution resolution = resolver.resolve(target, explicit);
            boolean inMenu = target.level() == PermissionLevel.TABLE
                    && resolution.effective().main() != MainPermission.NONE;
            boolean hasChildren = child.hasChildren() || (row != null && row.deeperRows() > 0);
            nodes.add(new PermissionNode(target, child.label(), explicit, resolution, inMenu, true, hasChildren));
        }
        stored.keySet().stream()
                .filter(name -> !inDsl.contains(name))
                .sorted(CODE_POINT_ORDER)
                .forEach(name -> {
                    StoredChild row = stored.get(name);
                    nodes.add(new PermissionNode(
                            targetOf.apply(name),
                            null,
                            row.explicit(),
                            notInDsl(),
                            false,
                            false,
                            row.deeperRows() > 0));
                });
        return List.copyOf(nodes);
    }

    private static PermissionInheritance.Resolution notInDsl() {
        return new PermissionInheritance.Resolution(
                EffectivePermission.NONE, InheritedFrom.DEFAULT, InheritedFrom.DEFAULT, InheritedFrom.DEFAULT);
    }

    private static Optional<DslSchema> findSchema(DslModel dsl, String schemaName) {
        return dsl.schemas().stream()
                .filter(schema -> schema.name().equals(schemaName))
                .findFirst();
    }

    /**
     * 対象が今の DSL にあるかを返す（大文字と小文字を区別して完全一致で照らす。BR4.3・BR4.6）。
     *
     * @param dsl 適用中の DSL のモデル
     * @param target 対象
     * @return 今の DSL にあれば true
     */
    public static boolean inCurrentDsl(DslModel dsl, PermissionTarget target) {
        Objects.requireNonNull(dsl, "dsl");
        Objects.requireNonNull(target, "target");
        Optional<DslSchema> schema = findSchema(dsl, target.schemaName());
        if (schema.isEmpty() || target.tableName() == null) {
            return schema.isPresent();
        }
        DslTable table = schema.get().tables().get(target.tableName());
        if (table == null || target.columnName() == null) {
            return table != null;
        }
        DslColumn column = table.columns().get(target.columnName());
        return column != null;
    }
}
