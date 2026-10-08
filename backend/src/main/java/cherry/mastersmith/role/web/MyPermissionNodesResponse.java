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
package cherry.mastersmith.role.web;

import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.role.domain.EffectiveNode;
import cherry.mastersmith.role.service.MyPermissionTree;
import java.util.List;

/**
 * 自分の権限の木の1階層の応答（契約 C8 の MyPermissionNodes、FS の 2.11）。
 *
 * @param workRole 有効な作業ロール（無ければ null）
 * @param items 節（DSL の順。DSL が無ければ空）
 */
public record MyPermissionNodesResponse(RoleRefResponse workRole, List<Item> items) {

    /**
     * 節1つ分。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（スキーマの節は null）
     * @param columnName カラムの名前（スキーマ・テーブルの節は null）
     * @param displayName 表示名（要求の言語で選んだ DSL の表示名）
     * @param effective 実効の値
     * @param inMenu 業務のメニューに出るか（テーブルの節だけ）
     * @param hasChildren 下の階層に DSL の節があるか
     */
    public record Item(
            String schemaName,
            String tableName,
            String columnName,
            String displayName,
            PermissionNodeResponse.Effective effective,
            boolean inMenu,
            boolean hasChildren) {

        static Item from(EffectiveNode node, DisplayLanguage language) {
            return new Item(
                    node.target().schemaName(),
                    node.target().tableName(),
                    node.target().columnName(),
                    language == DisplayLanguage.EN
                            ? node.label().en()
                            : node.label().ja(),
                    new PermissionNodeResponse.Effective(
                            node.effective().main(),
                            node.effective().create(),
                            node.effective().delete()),
                    node.inMenu(),
                    node.hasChildren());
        }
    }

    static MyPermissionNodesResponse from(MyPermissionTree tree, DisplayLanguage language) {
        return new MyPermissionNodesResponse(
                tree.workRole().map(RoleRefResponse::from).orElse(null),
                tree.nodes().stream().map(node -> Item.from(node, language)).toList());
    }
}
