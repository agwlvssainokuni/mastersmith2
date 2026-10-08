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
package cherry.mastersmith.role.service;

import cherry.mastersmith.role.domain.EffectiveNode;
import cherry.mastersmith.role.domain.WorkRoleRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 自分の権限の木の1階層（契約 C8 の MyPermissionNodes、FS の 2.11）。
 *
 * @param workRole 有効な作業ロール（無ければ空。すべて NONE・不可）
 * @param nodes 節（DSL の順。DSL が無ければ空）
 */
public record MyPermissionTree(Optional<WorkRoleRef> workRole, List<EffectiveNode> nodes) {

    /** 値を写して持つ。 */
    public MyPermissionTree {
        Objects.requireNonNull(workRole, "workRole");
        nodes = List.copyOf(nodes);
    }

    /** 作業ロールの ID と節の数だけを出す。 */
    @Override
    public String toString() {
        return "MyPermissionTree[workRoleId="
                + workRole.map(WorkRoleRef::roleId).orElse(null) + ", nodes=" + nodes.size() + "]";
    }
}
