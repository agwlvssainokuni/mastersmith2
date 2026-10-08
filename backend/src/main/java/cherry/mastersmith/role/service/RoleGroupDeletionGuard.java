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
import cherry.mastersmith.role.repository.AssignmentCount;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * group が定義する問う口（{@link GroupDeletionGuard}）の実装（契約 C4、{@code logical-components.md} の L5、BR10.1〜BR10.3、FS の 2.12）。
 *
 * <p>グループへのロールの割り当ての表（{@code group_role_assignments}）を、グループの ID の集合でまとめて1回で数える。{@link #canDelete}
 * は数が 0 なら {@link DeletionDecision.Allowed}、1 以上なら {@link DeletionDecision.Blocked}（数）で答え、{@link #assignedRoleCounts}
 * と同じ数で答える。読み取りだけで、呼び出し元（group）のトランザクションが有ればそれに入る。グループの削除はグループの行を排他してから
 * この口を呼び、グループへの割り当ては role がロールの行 → グループの行の順に排他するため、両者が重なってもグループの行で順が決まる
 * （AC2.1.5）。
 */
@Component
public class RoleGroupDeletionGuard implements GroupDeletionGuard {

    private final RoleAssignmentRepository assignments;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param assignments 割り当ての表の読み取り
     * @param transactionManager トランザクションの管理
     */
    public RoleGroupDeletionGuard(RoleAssignmentRepository assignments, PlatformTransactionManager transactionManager) {
        this.assignments = assignments;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public DeletionDecision canDelete(long groupId) {
        int count = assignedRoleCounts(Set.of(groupId)).get(groupId);
        return count == 0 ? new DeletionDecision.Allowed() : new DeletionDecision.Blocked(count);
    }

    @Override
    public Map<Long, Integer> assignedRoleCounts(Set<Long> groupIds) {
        Objects.requireNonNull(groupIds, "groupIds");
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        List<AssignmentCount> rows =
                Objects.requireNonNull(readOnly.execute(status -> assignments.countByGroups(Set.copyOf(groupIds))));
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            counts.put(groupId, 0);
        }
        for (AssignmentCount row : rows) {
            counts.put(row.id(), Math.toIntExact(row.count()));
        }
        return Map.copyOf(counts);
    }
}
