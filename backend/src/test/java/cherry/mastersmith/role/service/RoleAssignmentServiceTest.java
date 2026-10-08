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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.group.service.GroupMembershipQuery;
import cherry.mastersmith.group.service.GroupRowLock;
import cherry.mastersmith.group.service.GroupSummary;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleAuditFailure;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.domain.RoleSource;
import cherry.mastersmith.role.domain.UserRoleView;
import cherry.mastersmith.role.repository.AssignedRoleRow;
import cherry.mastersmith.role.repository.GroupAssignedRoleRow;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import cherry.mastersmith.role.repository.RoleRepository;
import cherry.mastersmith.role.repository.WorkRoleSelectionRepository;
import cherry.mastersmith.role.store.Referent;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserAdminSummary;
import cherry.mastersmith.user.service.UserSummary;
import cherry.mastersmith.useradmin.testsupport.RecordingTransactionManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 割り当てと外し、割り当ての読み取りの業務処理（FS の 2.8、BR6.1〜BR6.11・BR8.2・BR8.5・BR11、NFR 設計の読み直しの R-14、計画の 4.3・
 * 8.3）の単体テスト。store・repository・group と user の口・待ち合わせの口・出来事の知らせはモックにし、トランザクションは記録だけの
 * 管理に任せる。
 */
class RoleAssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");

    private static final long ACTOR = 1L;

    private static final long ROLE = 50L;

    private static final long USER = 8L;

    private static final long GROUP = 70L;

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.70", "Mozilla/5.0", "trace-0070");

    private final RoleStore store = mock(RoleStore.class);

    private final RoleRepository roles = mock(RoleRepository.class);

    private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);

    private final WorkRoleSelectionRepository selections = mock(WorkRoleSelectionRepository.class);

    private final UserAccountService userAccounts = mock(UserAccountService.class);

    private final GroupMembershipQuery groups = mock(GroupMembershipQuery.class);

    private final RoleBarrier barrier = mock(RoleBarrier.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    private final List<String> publishedAt = new ArrayList<>();

    private final List<RoleAuditEvent> events = new ArrayList<>();

    private final RoleAssignmentService service = new RoleAssignmentService(
            new RoleStoreTransactions(store, recording),
            roles,
            assignments,
            userAccounts,
            groups,
            new UserRoleReader(assignments, groups, selections),
            barrier,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            recording);

    RoleAssignmentServiceTest() {
        doAnswer(invocation -> {
                    events.add(invocation.getArgument(0));
                    publishedAt.add("rollbacks=" + recording.rollbacks() + ",commits=" + recording.commits());
                    return null;
                })
                .when(publisher)
                .publishEvent(any(Object.class));
        when(selections.findRoleIdOfUser(anyLong())).thenReturn(Optional.empty());
        when(groups.summaries(Set.of(GROUP))).thenReturn(List.of(new GroupSummary(GROUP, "第一営業部")));
    }

    private Role locked(String name) {
        Role role = mock(Role.class);
        when(role.getRoleId()).thenReturn(ROLE);
        when(role.getName()).thenReturn(name);
        when(store.lockRole(ROLE)).thenReturn(new RoleStoreOutcome.Done<>(role));
        return role;
    }

    private void userExists() {
        when(userAccounts.findById(USER))
                .thenReturn(Optional.of(
                        new UserSummary(USER, "member@example.com", false, "割り当て 一郎", "ja", "system", "md", false)));
    }

    private static UserAdminSummary summary(long userId) {
        return new UserAdminSummary(
                userId, "u" + userId + "@example.com", "割り当て 利用者" + userId, Language.JA, false, false, NOW);
    }

    private RoleAuditEvent onlyEvent() {
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private void failurePublishedAfterRollback() {
        assertThat(publishedAt).containsExactly("rollbacks=1,commits=0");
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);
        assertThat(recording.commits()).as("2つ目のトランザクション").isEqualTo(1);
    }

    @Test
    @DisplayName("assigning a user locks the role, checks the user and the pair, writes and records the user target")
    void assignUser() {
        Role role = locked("営業");
        userExists();
        when(store.assignToUser(role, USER, NOW)).thenReturn(new RoleStoreOutcome.Done<>(null));

        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER)).isEqualTo(new RoleAssignmentResult.Done());

        InOrder order = inOrder(barrier, store, userAccounts, assignments);
        order.verify(barrier).beforeLock(RoleOperation.ASSIGN, ROLE);
        order.verify(store).lockRole(ROLE);
        order.verify(barrier).afterLock(RoleOperation.ASSIGN, ROLE);
        order.verify(userAccounts).findById(USER);
        order.verify(assignments).existsUserAssignment(ROLE, USER);
        order.verify(barrier).afterCheck(RoleOperation.ASSIGN, "50:U8");
        order.verify(store).assignToUser(role, USER, NOW);
        order.verify(barrier).afterWrite(RoleOperation.ASSIGN, "50:U8");
        assertThat(onlyEvent())
                .isEqualTo(RoleAuditEvent.succeeded(
                        RoleOperation.ASSIGN,
                        ACTOR,
                        ROLE,
                        USER,
                        null,
                        new RoleAuditDetail.Assignment("営業", null),
                        NOW,
                        ORIGIN));
        assertThat(recording.rollbackOnlyMarks()).isZero();
    }

    @Test
    @DisplayName("the refusals follow the order role, user, pair and are recorded after rolling back (BR6.9)")
    void assignUserRefusals() {
        when(store.lockRole(ROLE)).thenReturn(new RoleStoreOutcome.RoleMissing<>());
        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.ROLE_NOT_FOUND));
        verifyNoInteractions(userAccounts);
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.ROLE_NOT_FOUND);
        assertThat(onlyEvent().detail()).isNull();
        assertThat(onlyEvent().targetUserId()).isEqualTo(USER);
        failurePublishedAfterRollback();

        events.clear();
        locked("営業");
        when(userAccounts.findById(USER)).thenReturn(Optional.empty());
        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.USER_NOT_FOUND));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.USER_NOT_FOUND);
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Assignment("営業", null));
        verify(assignments, never()).existsUserAssignment(anyLong(), anyLong());

        events.clear();
        userExists();
        when(assignments.existsUserAssignment(ROLE, USER)).thenReturn(true);
        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.NO_CHANGE));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.NO_CHANGE);
        verify(store, never()).assignToUser(any(), anyLong(), any());
    }

    @Test
    @DisplayName("a primary key violation of the pair is rolled back and recorded as ROLE_NO_CHANGE in the second step")
    void assignUserViolation() {
        Role role = locked("営業");
        userExists();
        when(store.assignToUser(role, USER, NOW)).thenReturn(new RoleStoreOutcome.AlreadyAssigned<>());

        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.NO_CHANGE));

        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.NO_CHANGE);
        assertThat(onlyEvent().operation()).isEqualTo(RoleOperation.ASSIGN);
        verify(barrier, never()).afterWrite(any(), any());
        failurePublishedAfterRollback();
    }

    @Test
    @DisplayName(
            "foreign key violations are read as the missing side, and a wait timeout is ROLE_BUSY without an event")
    void assignUserStoreOutcomes() {
        Role role = locked("営業");
        userExists();

        when(store.assignToUser(role, USER, NOW)).thenReturn(new RoleStoreOutcome.Referenced<>(Referent.USER));
        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.USER_NOT_FOUND));
        events.clear();
        when(store.assignToUser(role, USER, NOW)).thenReturn(new RoleStoreOutcome.Referenced<>(Referent.ROLE));
        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.ROLE_NOT_FOUND));
        assertThat(onlyEvent().detail()).isNull();
        events.clear();
        when(store.assignToUser(role, USER, NOW))
                .thenReturn(new RoleStoreOutcome.Busy<>(RoleStore.ROLE_ASSIGNMENT_KEY));
        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER)).isEqualTo(new RoleAssignmentResult.Busy());
        when(store.lockRole(ROLE)).thenReturn(new RoleStoreOutcome.Busy<>(RoleStore.ROLE_ROW));
        assertThat(service.assignUser(ACTOR, ORIGIN, ROLE, USER)).isEqualTo(new RoleAssignmentResult.Busy());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("assigning a group locks the role and then the group, and records the group name")
    void assignGroup() {
        Role role = locked("営業");
        when(groups.lockForAssignment(GROUP)).thenReturn(new GroupRowLock.Locked(true));
        when(store.assignToGroup(role, GROUP, NOW)).thenReturn(new RoleStoreOutcome.Done<>(null));

        assertThat(service.assignGroup(ACTOR, ORIGIN, ROLE, GROUP)).isEqualTo(new RoleAssignmentResult.Done());

        InOrder order = inOrder(store, groups, barrier);
        order.verify(store).lockRole(ROLE);
        order.verify(groups).lockForAssignment(GROUP);
        order.verify(barrier).afterCheck(RoleOperation.ASSIGN, "50:G70");
        order.verify(store).assignToGroup(role, GROUP, NOW);
        assertThat(onlyEvent().targetGroupId()).isEqualTo(GROUP);
        assertThat(onlyEvent().targetUserId()).isNull();
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Assignment("営業", "第一営業部"));
    }

    @Test
    @DisplayName("a busy or missing group refuses the group assignment, and an existing pair is ROLE_NO_CHANGE")
    void assignGroupRefusals() {
        locked("営業");
        when(groups.lockForAssignment(GROUP)).thenReturn(new GroupRowLock.Busy());
        assertThat(service.assignGroup(ACTOR, ORIGIN, ROLE, GROUP)).isEqualTo(new RoleAssignmentResult.GroupBusy());
        assertThat(events).isEmpty();
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);

        when(groups.lockForAssignment(GROUP)).thenReturn(new GroupRowLock.Locked(false));
        assertThat(service.assignGroup(ACTOR, ORIGIN, ROLE, GROUP))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.GROUP_NOT_FOUND));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.GROUP_NOT_FOUND);
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Assignment("営業", null));

        events.clear();
        when(groups.lockForAssignment(GROUP)).thenReturn(new GroupRowLock.Locked(true));
        when(assignments.existsGroupAssignment(ROLE, GROUP)).thenReturn(true);
        assertThat(service.assignGroup(ACTOR, ORIGIN, ROLE, GROUP))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.NO_CHANGE));
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Assignment("営業", "第一営業部"));
        verify(store, never()).assignToGroup(any(), anyLong(), any());
    }

    @Test
    @DisplayName("unassigning removes the pair or refuses with ROLE_NO_CHANGE when no row was removed")
    void unassignUser() {
        Role role = locked("営業");
        when(store.unassignFromUser(role, USER)).thenReturn(new RoleStoreOutcome.Done<>(true));
        assertThat(service.unassignUser(ACTOR, ORIGIN, ROLE, USER)).isEqualTo(new RoleAssignmentResult.Done());
        assertThat(onlyEvent())
                .isEqualTo(RoleAuditEvent.succeeded(
                        RoleOperation.UNASSIGN,
                        ACTOR,
                        ROLE,
                        USER,
                        null,
                        new RoleAuditDetail.Assignment("営業", null),
                        NOW,
                        ORIGIN));

        events.clear();
        when(store.unassignFromUser(role, USER)).thenReturn(new RoleStoreOutcome.Done<>(false));
        assertThat(service.unassignUser(ACTOR, ORIGIN, ROLE, USER))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.NO_CHANGE));
        assertThat(onlyEvent().failure()).isEqualTo(RoleAuditFailure.NO_CHANGE);
        verifyNoInteractions(userAccounts);
    }

    @Test
    @DisplayName("unassigning a missing group continues and is ROLE_NO_CHANGE, a busy group is GROUP_BUSY (BR6.6)")
    void unassignGroup() {
        Role role = locked("営業");
        when(groups.lockForAssignment(GROUP)).thenReturn(new GroupRowLock.Locked(false));
        when(store.unassignFromGroup(role, GROUP)).thenReturn(new RoleStoreOutcome.Done<>(false));

        assertThat(service.unassignGroup(ACTOR, ORIGIN, ROLE, GROUP))
                .isEqualTo(new RoleAssignmentResult.Rejected(RoleRejection.NO_CHANGE));
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Assignment("営業", null));
        assertThat(onlyEvent().targetGroupId()).isEqualTo(GROUP);

        events.clear();
        when(groups.lockForAssignment(GROUP)).thenReturn(new GroupRowLock.Locked(true));
        when(store.unassignFromGroup(role, GROUP)).thenReturn(new RoleStoreOutcome.Done<>(true));
        assertThat(service.unassignGroup(ACTOR, ORIGIN, ROLE, GROUP)).isEqualTo(new RoleAssignmentResult.Done());
        assertThat(onlyEvent().detail()).isEqualTo(new RoleAuditDetail.Assignment("営業", "第一営業部"));

        events.clear();
        when(groups.lockForAssignment(GROUP)).thenReturn(new GroupRowLock.Busy());
        assertThat(service.unassignGroup(ACTOR, ORIGIN, ROLE, GROUP)).isEqualTo(new RoleAssignmentResult.GroupBusy());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("the assignment list merges direct users and group members with one memberUserIds call (BR6.11)")
    void assignmentList() {
        when(roles.existsRole(ROLE)).thenReturn(true);
        when(assignments.findUserIdsOfRole(ROLE)).thenReturn(List.of(5L, 9L));
        when(assignments.findGroupIdsOfRole(ROLE)).thenReturn(List.of(71L, 70L));
        when(groups.summaries(Set.of(70L, 71L)))
                .thenReturn(List.of(new GroupSummary(70L, "第一営業部"), new GroupSummary(71L, "第二営業部")));
        when(groups.memberUserIds(Set.of(70L, 71L))).thenReturn(Map.of(70L, Set.of(9L, 6L), 71L, Set.of(9L)));
        when(userAccounts.findSummariesByIds(Set.of(5L, 6L, 9L)))
                .thenReturn(List.of(summary(5L), summary(6L), summary(9L)));

        RoleAssignmentsResult.Found found = (RoleAssignmentsResult.Found) service.assignments(ROLE);

        verify(groups, times(1)).memberUserIds(any());
        assertThat(found.users()).extracting(user -> user.user().userId()).containsExactly(5L, 6L, 9L);
        assertThat(found.users().get(0).sources()).containsExactly(new RoleSource.Direct());
        assertThat(found.users().get(1).sources()).containsExactly(new RoleSource.Group(70L, "第一営業部"));
        assertThat(found.users().get(2).sources())
                .containsExactly(
                        new RoleSource.Direct(),
                        new RoleSource.Group(70L, "第一営業部"),
                        new RoleSource.Group(71L, "第二営業部"));
        assertThat(found.groups()).extracting(GroupSummary::groupId).containsExactly(70L, 71L);
        assertThat(found.toString()).isEqualTo("Found[users=3, groups=2]").doesNotContain("example.com");
        assertThat(found.users().getFirst().toString()).doesNotContain("@").doesNotContain("割り当て");
        assertThat(recording.definitions()).allMatch(definition -> definition.isReadOnly());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("the readings of a missing role, a missing group and a missing user are refused without events")
    void readingsOfMissingTargets() {
        when(roles.existsRole(ROLE)).thenReturn(false);
        when(groups.exists(GROUP)).thenReturn(false);
        when(userAccounts.findById(USER)).thenReturn(Optional.empty());

        assertThat(service.assignments(ROLE)).isEqualTo(new RoleAssignmentsResult.RoleNotFound());
        assertThat(service.rolesOfGroup(GROUP)).isEqualTo(new GroupRolesResult.GroupNotFound());
        assertThat(service.rolesOfUser(USER)).isEqualTo(new UserRolesResult.UserNotFound());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("the roles of a group are in id order and the roles of a user carry DIRECT first and then the groups")
    void rolesOfGroupAndUser() {
        when(groups.exists(GROUP)).thenReturn(true);
        when(assignments.findRolesOfGroups(Set.of(GROUP)))
                .thenReturn(
                        List.of(new GroupAssignedRoleRow(GROUP, 3L, "経理"), new GroupAssignedRoleRow(GROUP, 4L, "人事")));
        assertThat(service.rolesOfGroup(GROUP))
                .isEqualTo(new GroupRolesResult.Found(List.of(new RoleRef(3L, "経理"), new RoleRef(4L, "人事"))));

        userExists();
        when(assignments.findDirectRolesOfUser(USER))
                .thenReturn(List.of(new AssignedRoleRow(3L, "経理"), new AssignedRoleRow(9L, "総務")));
        when(groups.groupIdsOfUser(USER)).thenReturn(Set.of(GROUP));
        UserRolesResult.Found found = (UserRolesResult.Found) service.rolesOfUser(USER);

        assertThat(found.roles())
                .containsExactly(
                        new UserRoleView(
                                3L, "経理", List.of(new RoleSource.Direct(), new RoleSource.Group(GROUP, "第一営業部"))),
                        new UserRoleView(4L, "人事", List.of(new RoleSource.Group(GROUP, "第一営業部"))),
                        new UserRoleView(9L, "総務", List.of(new RoleSource.Direct())));
    }
}
