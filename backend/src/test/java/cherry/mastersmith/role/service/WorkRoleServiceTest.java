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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.group.service.GroupMembershipQuery;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.repository.AssignedRoleRow;
import cherry.mastersmith.role.repository.GroupAssignedRoleRow;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import cherry.mastersmith.role.repository.WorkRoleSelectionRepository;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.useradmin.testsupport.RecordingTransactionManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 作業ロールの読み取りと切り替えの業務処理（FS の 2.10、BR7.1〜BR7.8・BR11.3・BR11.4、計画の 8.3）の単体テスト。
 */
class WorkRoleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T04:00:00Z");

    private static final long USER = 8L;

    private static final long GROUP = 70L;

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.80", "Mozilla/5.0", "trace-0080");

    private final RoleStore store = mock(RoleStore.class);

    private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);

    private final WorkRoleSelectionRepository selections = mock(WorkRoleSelectionRepository.class);

    private final GroupMembershipQuery groups = mock(GroupMembershipQuery.class);

    private final RoleBarrier barrier = mock(RoleBarrier.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    private final List<RoleAuditEvent> events = new ArrayList<>();

    private final WorkRoleService service = new WorkRoleService(
            new RoleStoreTransactions(store, recording),
            new UserRoleReader(assignments, groups, selections),
            barrier,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            recording);

    WorkRoleServiceTest() {
        doAnswer(invocation -> events.add(invocation.getArgument(0)))
                .when(publisher)
                .publishEvent(any(Object.class));
        when(assignments.findDirectRolesOfUser(USER))
                .thenReturn(List.of(new AssignedRoleRow(3L, "経理"), new AssignedRoleRow(9L, "総務")));
        when(groups.groupIdsOfUser(USER)).thenReturn(Set.of(GROUP));
        when(assignments.findRolesOfGroups(Set.of(GROUP)))
                .thenReturn(List.of(new GroupAssignedRoleRow(GROUP, 5L, "営業")));
        when(selections.findRoleIdOfUser(USER)).thenReturn(Optional.empty());
        when(store.saveWorkRoleSelection(anyLong(), anyLong(), any())).thenReturn(new RoleStoreOutcome.Done<>(null));
    }

    @Test
    @DisplayName("reading lists the direct and group roles in id order and reads the first role without a stored one")
    void readWithoutStored() {
        WorkRoleView view = service.read(USER);

        assertThat(view.roles())
                .containsExactly(new WorkRoleRef(3L, "経理"), new WorkRoleRef(5L, "営業"), new WorkRoleRef(9L, "総務"));
        assertThat(view.current()).contains(new WorkRoleRef(3L, "経理"));
        assertThat(recording.definitions()).allMatch(definition -> definition.isReadOnly());
        verify(store, never()).saveWorkRoleSelection(anyLong(), anyLong(), any());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName(
            "reading uses the stored role while assigned and reads it as the first role otherwise, without writing")
    void readWithStored() {
        when(selections.findRoleIdOfUser(USER)).thenReturn(Optional.of(5L));
        assertThat(service.read(USER).current()).contains(new WorkRoleRef(5L, "営業"));

        when(selections.findRoleIdOfUser(USER)).thenReturn(Optional.of(42L));
        assertThat(service.read(USER).current()).as("割り当ての外の保存は読み替える").contains(new WorkRoleRef(3L, "経理"));
        verify(store, never()).saveWorkRoleSelection(anyLong(), anyLong(), any());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("switching to an assigned role writes the selection and records the previous and the chosen role")
    void switchWrites() {
        assertThat(service.switchTo(USER, ORIGIN, 5L)).isEqualTo(new WorkRoleSwitchResult.Switched());

        InOrder order = inOrder(barrier, store);
        order.verify(barrier).afterCheck(RoleOperation.SWITCH_WORK_ROLE, "8");
        order.verify(store).saveWorkRoleSelection(USER, 5L, NOW);
        order.verify(barrier).afterWrite(RoleOperation.SWITCH_WORK_ROLE, "8");
        verify(store, never()).lockRole(anyLong());
        assertThat(events)
                .containsExactly(RoleAuditEvent.succeeded(
                        RoleOperation.SWITCH_WORK_ROLE,
                        USER,
                        5L,
                        USER,
                        null,
                        new RoleAuditDetail.WorkRoleSwitch(3L, "経理", "営業", null),
                        NOW,
                        ORIGIN));
    }

    @Test
    @DisplayName("switching to the stored role writes nothing and records nothing (BR7.6)")
    void switchToTheStoredRole() {
        when(selections.findRoleIdOfUser(USER)).thenReturn(Optional.of(5L));

        assertThat(service.switchTo(USER, ORIGIN, 5L)).isEqualTo(new WorkRoleSwitchResult.Unchanged());

        verify(store, never()).saveWorkRoleSelection(anyLong(), anyLong(), any());
        assertThat(events).isEmpty();
        assertThat(recording.rollbackOnlyMarks()).isZero();
    }

    @Test
    @DisplayName("while the stored role is read as another one, choosing the effective role writes and records it")
    void switchWhileReinterpreted() {
        when(selections.findRoleIdOfUser(USER)).thenReturn(Optional.of(42L));

        assertThat(service.switchTo(USER, ORIGIN, 3L)).isEqualTo(new WorkRoleSwitchResult.Switched());

        verify(store).saveWorkRoleSelection(USER, 3L, NOW);
        assertThat(events)
                .singleElement()
                .satisfies(event ->
                        assertThat(event.detail()).isEqualTo(new RoleAuditDetail.WorkRoleSwitch(3L, "経理", "経理", 42L)));
    }

    @Test
    @DisplayName("a role outside the user's roles and a missing role are refused alike after rolling back (BR7.5)")
    void notAssigned() {
        for (long requested : new long[] {4L, 999_999L, 0L, -1L}) {
            events.clear();
            assertThat(service.switchTo(USER, ORIGIN, requested)).isEqualTo(new WorkRoleSwitchResult.NotAssigned());
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.failure()).isEqualTo(RoleAuditFailure.ROLE_NOT_ASSIGNED);
                assertThat(event.targetRoleId()).isEqualTo(requested);
                assertThat(event.targetUserId()).isEqualTo(USER);
                assertThat(event.actorUserId()).isEqualTo(USER);
                assertThat(event.detail()).isNull();
            });
        }
        verify(store, never()).saveWorkRoleSelection(anyLong(), anyLong(), any());
        verify(barrier, never()).afterCheck(any(), any());
    }

    @Test
    @DisplayName("a busy selection key is ROLE_BUSY without an event")
    void busy() {
        when(store.saveWorkRoleSelection(USER, 9L, NOW))
                .thenReturn(new RoleStoreOutcome.Busy<>(RoleStore.WORK_ROLE_SELECTION_KEY));

        assertThat(service.switchTo(USER, ORIGIN, 9L)).isEqualTo(new WorkRoleSwitchResult.Busy());

        assertThat(events).isEmpty();
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);
        verify(barrier, never()).afterWrite(any(), any());
    }
}
