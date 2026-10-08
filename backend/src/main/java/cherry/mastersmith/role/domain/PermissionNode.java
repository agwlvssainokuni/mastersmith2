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
import java.util.Objects;

/**
 * 権限の設定の木の1つの節（契約 C7 の PermissionNodes の1件、BR4.10・BR4.11）。
 *
 * @param target 対象
 * @param label DSL の表示名（今の DSL に無い節は null。応答では物理名で代える）
 * @param explicit 明示の値（行が無ければすべて設定なし）
 * @param inheritance 実効の値と継承の元（主権限・CREATE・DELETE ごと。今の DSL に無い節は NONE・不可・DEFAULT）
 * @param inMenu 業務のメニューに出るか（テーブルの節だけ。実効の主権限が NONE でないとき真）
 * @param inCurrentDsl 今の DSL にあるか
 * @param hasChildren 下の階層に DSL の節か設定があるか
 */
public record PermissionNode(
        PermissionTarget target,
        DisplayName label,
        PermissionValues explicit,
        PermissionInheritance.Resolution inheritance,
        boolean inMenu,
        boolean inCurrentDsl,
        boolean hasChildren) {

    /** 値が null でないことを確かめる（表示名は今の DSL に無い節だけ null）。 */
    public PermissionNode {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(explicit, "explicit");
        Objects.requireNonNull(inheritance, "inheritance");
        if (inCurrentDsl != (label != null)) {
            throw new IllegalArgumentException("表示名は今の DSL にある節だけが持ちます");
        }
    }

    /**
     * 実効の値を返す。
     *
     * @return 実効の値
     */
    public EffectivePermission effective() {
        return inheritance.effective();
    }

    /**
     * 名前（その階層の物理名）を返す。
     *
     * @return 名前
     */
    public String name() {
        return switch (target.level()) {
            case SCHEMA -> target.schemaName();
            case TABLE -> target.tableName();
            case COLUMN -> target.columnName();
        };
    }
}
