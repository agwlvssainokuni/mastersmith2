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
 * 自分の権限の木の1つの節（契約 C8 の MyPermissionNodes の1件、FS の 2.11）。今の DSL の節だけで、実効の値と {@code inMenu} を持つ。
 *
 * @param target 対象
 * @param label DSL の表示名
 * @param effective 実効の値
 * @param inMenu 業務のメニューに出るか（テーブルの節だけ。実効の主権限が NONE でないとき真）
 * @param hasChildren 下の階層に DSL の節があるか
 */
public record EffectiveNode(
        PermissionTarget target,
        DisplayName label,
        EffectivePermission effective,
        boolean inMenu,
        boolean hasChildren) {

    /** 値が null でないことを確かめる。 */
    public EffectiveNode {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(effective, "effective");
    }
}
