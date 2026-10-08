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

import cherry.mastersmith.role.domain.WorkRoleRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 自分の作業ロールの読み取りの結果（契約 C8 の {@code GET /api/me/work-role}、FS の 2.10、BR7.1・BR7.2）。
 *
 * @param roles 利用者のロール（ロールの ID の順。先頭が「最初のロール」）
 * @param current 有効な作業ロール（無ければ空。読み替えは保存を書き換えない）
 */
public record WorkRoleView(List<WorkRoleRef> roles, Optional<WorkRoleRef> current) {

    /** 値を写して持つ。 */
    public WorkRoleView {
        roles = List.copyOf(roles);
        Objects.requireNonNull(current, "current");
    }

    /** 件数と作業ロールの ID だけを出す。 */
    @Override
    public String toString() {
        return "WorkRoleView[roles=" + roles.size() + ", current="
                + current.map(WorkRoleRef::roleId).orElse(null) + "]";
    }
}
