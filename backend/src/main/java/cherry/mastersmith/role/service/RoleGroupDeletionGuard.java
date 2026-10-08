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

import cherry.mastersmith.group.service.DeletionDecision;
import cherry.mastersmith.group.service.GroupDeletionGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * group が定義する問う口（{@link GroupDeletionGuard}）の B3 の仮の実装（Intent 261004-role-menu の U3、BR6.3、計画の 11節 Q1: A・D-19）。
 *
 * <p>グループへのロールの割り当ての表がまだ無いため、渡した ID のすべてに割り当ての数 0 を返し、削除してよい（{@link
 * DeletionDecision.Allowed}）と答える。B5 で U4 role が本物（割り当ての表を数える実装）に置き換え、この仮の実装が残っていないことを B5 の
 * 終わりの条件にする（U4 role の BR10、{@code logical-components.md} の L5）。割り当てが残るときの拒否は、B3 ではテスト用の実装
 * （{@code group/testsupport}、{@code @Primary}）で確かめる。
 */
// TODO(B5): 割り当ての表を数える本物の実装に置き換える（U4 role の BR10）。
@Component
public class RoleGroupDeletionGuard implements GroupDeletionGuard {

    @Override
    public DeletionDecision canDelete(long groupId) {
        return new DeletionDecision.Allowed();
    }

    @Override
    public Map<Long, Integer> assignedRoleCounts(Set<Long> groupIds) {
        Objects.requireNonNull(groupIds, "groupIds");
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            counts.put(groupId, 0);
        }
        return Map.copyOf(counts);
    }
}
