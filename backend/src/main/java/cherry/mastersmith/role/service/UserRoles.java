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

import cherry.mastersmith.role.domain.WorkRoleChooser;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.repository.AssignedRoleRow;
import cherry.mastersmith.role.repository.GroupAssignedRoleRow;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

/**
 * 利用者のロールを決める読み取りの結果（直接の割り当て・所属グループへの割り当て・作業ロールの保存。BR6.5・BR7.1・BR7.2、計画の D-15）。
 *
 * <p>有効な作業ロールは {@link WorkRoleChooser} だけで決める（解決の口と作業ロールの API が同じ決め方を使う。契約 C5 の「有効な作業ロールを
 * 決める唯一の持ち主」）。文字列にしたときは数だけを出す（計画の D-5）。
 *
 * @param direct 直接の割り当てのロール（ロールの ID の順）
 * @param viaGroups 所属グループへの割り当てのロール（ロールの ID・グループの ID の順）
 * @param stored 保存した作業ロールの ID（無ければ空）
 */
public record UserRoles(List<AssignedRoleRow> direct, List<GroupAssignedRoleRow> viaGroups, OptionalLong stored) {

    /** 値を写して持つ。 */
    public UserRoles {
        direct = List.copyOf(direct);
        viaGroups = List.copyOf(viaGroups);
        Objects.requireNonNull(stored, "stored");
    }

    /**
     * 利用者のロールを、ロールの ID の順に重なりなしで返す（BR6.5・BR7.1）。
     *
     * @return ロール（直接とグループ経由の和）
     */
    public List<WorkRoleRef> roles() {
        Map<Long, String> byId = new TreeMap<>();
        direct.forEach(row -> byId.put(row.roleId(), row.name()));
        viaGroups.forEach(row -> byId.putIfAbsent(row.roleId(), row.name()));
        List<WorkRoleRef> roles = new ArrayList<>();
        byId.forEach((id, name) -> roles.add(new WorkRoleRef(id, name)));
        return List.copyOf(roles);
    }

    /**
     * 利用者のロールの ID を返す。
     *
     * @return ロールの ID（ID の順）
     */
    public Set<Long> roleIds() {
        Set<Long> ids = new LinkedHashSet<>();
        roles().forEach(role -> ids.add(role.roleId()));
        return ids;
    }

    /**
     * 有効な作業ロールを返す（BR7.2。読み替えは保存を書き換えない。BR7.3）。
     *
     * @return 有効な作業ロール（ロールが無ければ空）
     */
    public Optional<WorkRoleRef> effective() {
        List<WorkRoleRef> roles = roles();
        OptionalLong chosen =
                WorkRoleChooser.choose(roles.stream().map(WorkRoleRef::roleId).toList(), stored);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        long id = chosen.getAsLong();
        return roles.stream().filter(role -> role.roleId() == id).findFirst();
    }

    /** 数だけを出す（ロールの名前を出さない）。 */
    @Override
    public String toString() {
        return "UserRoles[direct=" + direct.size() + ", viaGroups=" + viaGroups.size() + ", stored="
                + (stored.isPresent() ? stored.getAsLong() : null) + "]";
    }
}
