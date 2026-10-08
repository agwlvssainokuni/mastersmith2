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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.group.service.DeletionDecision;
import cherry.mastersmith.role.repository.AssignmentCount;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import cherry.mastersmith.useradmin.testsupport.RecordingTransactionManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * group の問う口の実装（BR10.1〜BR10.3、FS の 2.12、計画の 8.3）の単体テスト。B3 の仮の実装（常に 0・削除してよい）を置き換えたことを、
 * 割り当ての表の数で答えることで確かめる（仮の実装が残っていれば落ちる）。
 */
class RoleGroupDeletionGuardTest {

    private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    private final RoleGroupDeletionGuard guard = new RoleGroupDeletionGuard(assignments, recording);

    @Test
    @DisplayName("every given group id gets a key, the counted ones their count and the others zero, in one query")
    void countsForEveryId() {
        when(assignments.countByGroups(any())).thenReturn(List.of(new AssignmentCount(2L, 3L)));

        assertThat(guard.assignedRoleCounts(Set.of(1L, 2L, 3L))).isEqualTo(Map.of(1L, 0, 2L, 3, 3L, 0));
        verify(assignments).countByGroups(Set.of(1L, 2L, 3L));
        assertThat(recording.definitions()).singleElement().matches(definition -> definition.isReadOnly());
    }

    @Test
    @DisplayName("an empty set gives an empty map without a query")
    void emptySet() {
        assertThat(guard.assignedRoleCounts(Set.of())).isEmpty();
        verifyNoInteractions(assignments);
        assertThatThrownBy(() -> guard.assignedRoleCounts(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a group without assigned roles may be deleted")
    void allowed() {
        when(assignments.countByGroups(any())).thenReturn(List.of());

        DeletionDecision decision = guard.canDelete(7L);

        assertThat(decision).isEqualTo(new DeletionDecision.Allowed());
        assertThat(decision.assignedRoles()).isZero();
    }

    @Test
    @DisplayName("a group with assigned roles is blocked with the same count as assignedRoleCounts")
    void blocked() {
        when(assignments.countByGroups(any())).thenReturn(List.of(new AssignmentCount(7L, 2L)));

        assertThat(guard.canDelete(7L)).isEqualTo(new DeletionDecision.Blocked(2));
        assertThat(guard.assignedRoleCounts(Set.of(7L))).containsEntry(7L, 2);
    }
}
