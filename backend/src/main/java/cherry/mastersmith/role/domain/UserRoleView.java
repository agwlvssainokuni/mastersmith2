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

import java.util.List;
import java.util.Objects;

/**
 * 利用者のロールと出どころ（{@code entities.md} の UserRoleView、契約 C7 の UserRole・C8 の roles の元、BR6.5）。
 *
 * @param roleId ロールの ID
 * @param name ロールの名前
 * @param sources 出どころ（直接を先に、グループはグループの ID の順。1つ以上）
 */
public record UserRoleView(long roleId, String name, List<RoleSource> sources) {

    /** 値が null でなく、出どころが1つ以上あることを確かめ、写して持つ。 */
    public UserRoleView {
        Objects.requireNonNull(name, "name");
        sources = List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("出どころは1つ以上です");
        }
    }

    /**
     * 作業ロールの参照にする。
     *
     * @return 参照
     */
    public WorkRoleRef toRef() {
        return new WorkRoleRef(roleId, name);
    }
}
