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
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.role.domain.EffectivePermission;
import cherry.mastersmith.role.domain.InheritedFrom;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionNode;
import cherry.mastersmith.role.domain.PermissionValues;

/**
 * 権限の設定の木の1つの節の応答（契約 C7 の PermissionNodes の1件、BR4.11）。
 *
 * <p>{@code displayName} は DSL の表示名を要求の言語（Accept-Language）で選んだ文字列で、今の DSL に無い節は物理名。
 * {@code inheritedFrom} は主権限の継承の元（契約 C7 の1つの項目。計画との差は {@code generation-notes.md}）。
 *
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前（スキーマの節は null）
 * @param columnName カラムの名前（スキーマ・テーブルの節は null）
 * @param displayName 表示名
 * @param explicit 明示の値（null は設定なし）
 * @param effective 実効の値
 * @param inheritedFrom 主権限の継承の元
 * @param inMenu 業務のメニューに出るか（テーブルの節だけ）
 * @param inCurrentDsl 今の DSL にあるか
 * @param hasChildren 下の階層があるか
 */
public record PermissionNodeResponse(
        String schemaName,
        String tableName,
        String columnName,
        String displayName,
        Explicit explicit,
        Effective effective,
        InheritedFrom inheritedFrom,
        boolean inMenu,
        boolean inCurrentDsl,
        boolean hasChildren) {

    /**
     * 明示の値（null は設定なし）。
     *
     * @param main 主権限
     * @param create CREATE
     * @param delete DELETE
     */
    public record Explicit(MainPermission main, Boolean create, Boolean delete) {}

    /**
     * 実効の値。
     *
     * @param main 主権限
     * @param create CREATE
     * @param delete DELETE
     */
    public record Effective(MainPermission main, boolean create, boolean delete) {}

    /**
     * 節から作る。
     *
     * @param node 節
     * @param language 表示の言語
     * @return 応答
     */
    static PermissionNodeResponse from(PermissionNode node, DisplayLanguage language) {
        PermissionValues explicit = node.explicit();
        EffectivePermission effective = node.effective();
        return new PermissionNodeResponse(
                node.target().schemaName(),
                node.target().tableName(),
                node.target().columnName(),
                displayName(node, language),
                new Explicit(explicit.main(), explicit.create(), explicit.delete()),
                new Effective(effective.main(), effective.create(), effective.delete()),
                node.inheritance().mainFrom(),
                node.inMenu(),
                node.inCurrentDsl(),
                node.hasChildren());
    }

    private static String displayName(PermissionNode node, DisplayLanguage language) {
        DisplayName label = node.label();
        if (label == null) {
            return node.name();
        }
        return language == DisplayLanguage.EN ? label.en() : label.ja();
    }
}
