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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.group.service.DeletionDecision;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** group の問う口の B3 の仮の実装（BR6.2・BR6.3、計画の 11節 Q1: A）の単体テスト。B5 で本物に置き換える。 */
class RoleGroupDeletionGuardTest {

    private final RoleGroupDeletionGuard guard = new RoleGroupDeletionGuard();

    @Test
    @DisplayName("every given group id gets a count of zero, and an empty set gives an empty map")
    void zeroForEveryId() {
        assertThat(guard.assignedRoleCounts(Set.of(1L, 2L, 3L))).isEqualTo(Map.of(1L, 0, 2L, 0, 3L, 0));
        assertThat(guard.assignedRoleCounts(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("every group may be deleted with no assigned role")
    void allowed() {
        DeletionDecision decision = guard.canDelete(1L);

        assertThat(decision).isEqualTo(new DeletionDecision.Allowed());
        assertThat(decision.assignedRoles()).isZero();
        assertThat(new DeletionDecision.Blocked(3).assignedRoles()).isEqualTo(3);
    }
}
