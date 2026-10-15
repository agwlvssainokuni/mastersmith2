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
package cherry.mastersmith.navigation.domain;

import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 業務のメニューを作業ロールの実効の主権限で絞る純粋な関数（{@code rules.md} の BR2.1〜BR2.7・BR3.1・BR3.5、{@code functional-spec.md}
 * の 2.3）。副作用を持たず、同じ入力には同じ結果を返す。
 *
 * <p>項目ごとに深さ優先で子から先に決める。
 *
 * <ul>
 *   <li>テーブルを持ち、そのテーブルの主権限が READ・FULL なら、移れる項目として出す（子は絞った後の子、空でもよい）
 *   <li>テーブルを持たない、またはテーブルが見えない項目は、絞った後の子が1つ以上なら移れないまとまりとして出し、0 なら落とす（落とした
 *       結果は親でも同じ判定でさかのぼる）
 *   <li>判定はテーブルの階層の主権限だけで、カラムの値は見ない。DSL に無い組は NONE として落ちる（特別な分岐を持たない）
 *   <li>{@code id} は絞る前の DSL の木での位置の道（各段の 0 から数えた番号を {@code .} でつなぐ）。落とした項目があっても番号を詰めない
 *   <li>残った項目の親子と並びは元の木と同じ（並べ替え・付け替え・深さの変更をしない）
 * </ul>
 *
 * <p>深さの前提: 渡されるメニューは、DSL の深さの上限（{@link cherry.mastersmith.dsl.domain.DslFormat#MAX_MENU_DEPTH} = 5 段）の
 * 検証を通ったモデルのものだけである（投入・適用の検証と、起動時の読み込みで深すぎる枝を落とす処理が守る）。この関数は深さを数えず、
 * 上限を超えた木を拒否もしない。入れ子が上限の内に収まるため、再帰でたどってもスタックを使い尽くさない。深さの境界（上限ちょうどは
 * 受け付け、超えると拒否する）のテストの持ち主は dsl の部品（{@code MenuDepthTest}・{@code MenuDepthPropertyTest}）で、この関数の
 * テストは境界を確かめない（Intent 261015-deps-ui-carryover の FR4.2、前の Intent のレビューの navigation R-04）。
 */
public final class MenuFilter {

    private MenuFilter() {}

    /**
     * メニューを絞る。
     *
     * @param menus DSL の最上位のメニュー
     * @param input 主権限とアイコンの操作
     * @return 絞った後の最上位の項目（DSL の順。空を許す）
     */
    public static List<NavNode> filter(List<DslMenuItem> menus, MenuFilterInput input) {
        Objects.requireNonNull(menus, "menus は必須です");
        Objects.requireNonNull(input, "input は必須です");
        return filterLevel(menus, null, input);
    }

    private static List<NavNode> filterLevel(List<DslMenuItem> items, String parentPath, MenuFilterInput input) {
        List<NavNode> kept = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String path = parentPath == null ? Integer.toString(i) : parentPath + "." + i;
            NavNode node = filterItem(items.get(i), path, input);
            if (node != null) {
                kept.add(node);
            }
        }
        return kept;
    }

    /** 項目1つを絞る。出さない項目は null を返す。 */
    private static NavNode filterItem(DslMenuItem item, String path, MenuFilterInput input) {
        List<NavNode> children = filterLevel(item.items(), path, input);
        TableRef table = item.table();
        boolean visible = table != null && isVisible(input.tableMain().apply(table.schema(), table.name()));
        if (!visible && children.isEmpty()) {
            return null;
        }
        return new NavNode(
                path,
                DisplayLabel.of(item.label()),
                input.iconOf().apply(item.icon()),
                visible ? new NavTableRef(table.schema(), table.name()) : null,
                visible,
                children);
    }

    private static boolean isVisible(MainPermission main) {
        Objects.requireNonNull(main, "主権限の操作は null を返しません");
        return main != MainPermission.NONE;
    }
}
