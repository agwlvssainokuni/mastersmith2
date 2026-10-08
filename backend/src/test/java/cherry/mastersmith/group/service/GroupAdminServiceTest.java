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
package cherry.mastersmith.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.group.domain.Group;
import cherry.mastersmith.group.domain.GroupAuditDetail;
import cherry.mastersmith.group.domain.GroupAuditEvent;
import cherry.mastersmith.group.domain.GroupAuditFailure;
import cherry.mastersmith.group.domain.GroupMember;
import cherry.mastersmith.group.domain.GroupNameValidation;
import cherry.mastersmith.group.domain.GroupOperation;
import cherry.mastersmith.group.domain.GroupRejection;
import cherry.mastersmith.group.repository.GroupMemberCount;
import cherry.mastersmith.group.repository.GroupMemberRepository;
import cherry.mastersmith.group.repository.GroupMemberRow;
import cherry.mastersmith.group.repository.GroupRepository;
import cherry.mastersmith.group.repository.GroupRowView;
import cherry.mastersmith.group.store.GroupStore;
import cherry.mastersmith.group.store.StoreOutcome;
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
 * グループの管理の業務処理（FS の 2.1〜2.7、BR3.5・BR4.1〜BR4.4・BR5.1・BR5.2・BR5.4・BR5.7・BR8.1〜BR8.3、NFR3.4、計画の D-5・D-6）の
 * 単体テスト。store・repository・問う口・待ち合わせの口・出来事の知らせはモックにし、トランザクションは記録だけの管理に任せる。
 */
class GroupAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");

    private static final long ACTOR = 1L;

    private static final long GROUP = 70L;

    private static final long USER = 80L;

    private static final RequestOrigin ORIGIN = new RequestOrigin("192.0.2.70", "Mozilla/5.0", "trace-0070");

    private final GroupStore store = mock(GroupStore.class);

    private final GroupRepository groups = mock(GroupRepository.class);

    private final GroupMemberRepository members = mock(GroupMemberRepository.class);

    private final UserAccountService userAccounts = mock(UserAccountService.class);

    private final GroupDeletionGuard guard = mock(GroupDeletionGuard.class);

    private final GroupBarrier barrier = mock(GroupBarrier.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final RecordingTransactionManager recording = new RecordingTransactionManager();

    /** 出来事を知らせた時点の、巻き戻しの回数と確定の回数（出来事を出したトランザクションの見分けに使う）。 */
    private final List<String> publishedAt = new ArrayList<>();

    private final List<GroupAuditEvent> events = new ArrayList<>();

    private final GroupAdminService service = new GroupAdminService(
            new GroupStoreTransactions(store, recording),
            groups,
            members,
            userAccounts,
            guard,
            barrier,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            recording);

    GroupAdminServiceTest() {
        doAnswer(invocation -> {
                    events.add(invocation.getArgument(0));
                    publishedAt.add("rollbacks=" + recording.rollbacks() + ",commits=" + recording.commits());
                    return null;
                })
                .when(publisher)
                .publishEvent(any(Object.class));
    }

    private static Group group(long id, String name) {
        Group group = mock(Group.class);
        when(group.getGroupId()).thenReturn(id);
        when(group.getName()).thenReturn(name);
        when(group.getCreatedAt()).thenReturn(NOW);
        when(group.getUpdatedAt()).thenReturn(NOW);
        return group;
    }

    private void locked(String name) {
        Group locked = group(GROUP, name);
        when(store.lockGroup(GROUP)).thenReturn(new StoreOutcome.Done<>(locked));
    }

    private void userExists() {
        UserSummary summary = new UserSummary(USER, "member@example.com", false, "営業 太郎", "ja", "system", "md", false);
        when(userAccounts.findById(USER)).thenReturn(Optional.of(summary));
    }

    private GroupAuditEvent onlyEvent() {
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    /** 1つ目を巻き戻した後の、2つ目のトランザクションで失敗の出来事を出したことを確かめる。 */
    private void failurePublishedAfterRollback() {
        assertThat(publishedAt).containsExactly("rollbacks=1,commits=0");
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(1);
        assertThat(recording.rollbacks()).isEqualTo(1);
        assertThat(recording.commits()).as("2つ目のトランザクション").isEqualTo(1);
    }

    @Test
    @DisplayName("an invalid name is refused before any transaction, store call or event")
    void invalidNames() {
        assertThat(service.create(ACTOR, ORIGIN, " 　 "))
                .isEqualTo(new GroupCreateResult.Invalid(GroupNameValidation.Reason.INVALID_BLANK));
        assertThat(service.rename(ACTOR, ORIGIN, GROUP, "a".repeat(65)))
                .isEqualTo(new GroupChangeResult.Invalid(GroupNameValidation.Reason.INVALID_TOO_LONG));
        assertThat(service.rename(ACTOR, ORIGIN, GROUP, "営業\n部"))
                .isEqualTo(new GroupChangeResult.Invalid(GroupNameValidation.Reason.INVALID_CONTROL_CHARACTER));

        assertThat(recording.definitions()).isEmpty();
        verifyNoInteractions(store, publisher, barrier);
    }

    @Test
    @DisplayName("a creation checks the key, waits at the check and write points, writes and commits one success event")
    void createSucceeds() {
        Group created = group(GROUP, "営業部");
        when(groups.findIdByNameKey("営業部")).thenReturn(Optional.empty());
        when(store.insertGroup(any(), eq(NOW))).thenReturn(new StoreOutcome.Done<>(created));

        GroupCreateResult result = service.create(ACTOR, ORIGIN, " 営業部 ");

        assertThat(result).isEqualTo(new GroupCreateResult.Created(new GroupView(GROUP, "営業部", NOW, NOW)));
        InOrder order = inOrder(groups, barrier, store);
        order.verify(groups).findIdByNameKey("営業部");
        order.verify(barrier).afterCheck(GroupOperation.CREATE, "営業部");
        order.verify(store).insertGroup(any(), eq(NOW));
        order.verify(barrier).afterWrite(GroupOperation.CREATE, "営業部");
        assertThat(onlyEvent())
                .isEqualTo(GroupAuditEvent.succeeded(
                        GroupOperation.CREATE, ACTOR, GROUP, null, new GroupAuditDetail.Name("営業部"), NOW, ORIGIN));
        assertThat(publishedAt).containsExactly("rollbacks=0,commits=0");
        assertThat(recording.commits()).isEqualTo(1);
        assertThat(recording.rollbackOnlyMarks()).isZero();
    }

    @Test
    @DisplayName(
            "a creation whose key exists, or that hits the unique violation, is a duplicate audited after rollback")
    void createDuplicate() {
        when(groups.findIdByNameKey("sales")).thenReturn(Optional.of(5L));

        assertThat(service.create(ACTOR, ORIGIN, "Sales"))
                .isEqualTo(new GroupCreateResult.Rejected(GroupRejection.NAME_DUPLICATE));
        verify(store, never()).insertGroup(any(), any());
        failurePublishedAfterRollback();
        assertThat(onlyEvent())
                .isEqualTo(GroupAuditEvent.failed(
                        GroupOperation.CREATE,
                        ACTOR,
                        null,
                        null,
                        GroupAuditFailure.GROUP_NAME_DUPLICATE,
                        new GroupAuditDetail.Name("Sales"),
                        NOW,
                        ORIGIN));

        GroupAdminServiceTest violated = new GroupAdminServiceTest();
        when(violated.groups.findIdByNameKey("sales")).thenReturn(Optional.empty());
        when(violated.store.insertGroup(any(), any())).thenReturn(new StoreOutcome.NameTaken<>());
        assertThat(violated.service.create(ACTOR, ORIGIN, "Sales"))
                .isEqualTo(new GroupCreateResult.Rejected(GroupRejection.NAME_DUPLICATE));
        violated.failurePublishedAfterRollback();
        assertThat(violated.onlyEvent().failure()).isEqualTo(GroupAuditFailure.GROUP_NAME_DUPLICATE);
        assertThat(violated.onlyEvent().targetGroupId()).isNull();
        verify(violated.barrier, never()).afterWrite(any(), any());
    }

    @Test
    @DisplayName("a wait timeout is Busy: rolled back, no event and no write point")
    void busyHasNoEvent() {
        when(groups.findIdByNameKey(any())).thenReturn(Optional.empty());
        when(store.insertGroup(any(), any())).thenReturn(new StoreOutcome.Busy<>(GroupStore.GROUP_NAME_KEY));
        when(store.lockGroup(GROUP)).thenReturn(new StoreOutcome.Busy<>(GroupStore.GROUP_ROW));

        assertThat(service.create(ACTOR, ORIGIN, "営業部")).isEqualTo(new GroupCreateResult.Busy());
        assertThat(service.rename(ACTOR, ORIGIN, GROUP, "総務部")).isEqualTo(new GroupChangeResult.Busy());
        assertThat(service.delete(ACTOR, ORIGIN, GROUP)).isEqualTo(new GroupChangeResult.Busy());
        assertThat(service.addMember(ACTOR, ORIGIN, GROUP, USER)).isEqualTo(new GroupChangeResult.Busy());
        assertThat(service.removeMember(ACTOR, ORIGIN, GROUP, USER)).isEqualTo(new GroupChangeResult.Busy());

        assertThat(events).isEmpty();
        assertThat(recording.rollbackOnlyMarks()).isEqualTo(5);
        assertThat(recording.rollbacks()).isEqualTo(5);
        assertThat(recording.commits()).isZero();
        verify(barrier, never()).afterLock(any(), anyLong());
        verify(barrier, never()).afterWrite(any(), any());
    }

    @Test
    @DisplayName(
            "a rename locks first, refuses a missing group, the same name and another group's key, allows a case change")
    void rename() {
        when(store.lockGroup(GROUP)).thenReturn(new StoreOutcome.GroupMissing<>());
        assertThat(service.rename(ACTOR, ORIGIN, GROUP, "総務部"))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.GROUP_NOT_FOUND));
        assertThat(onlyEvent())
                .isEqualTo(GroupAuditEvent.failed(
                        GroupOperation.RENAME,
                        ACTOR,
                        GROUP,
                        null,
                        GroupAuditFailure.GROUP_NOT_FOUND,
                        null,
                        NOW,
                        ORIGIN));

        GroupAdminServiceTest same = new GroupAdminServiceTest();
        same.locked("Sales");
        assertThat(same.service.rename(ACTOR, ORIGIN, GROUP, " Sales "))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.NO_CHANGE));
        assertThat(same.onlyEvent().detail()).isEqualTo(new GroupAuditDetail.Name("Sales"));
        assertThat(same.onlyEvent().failure()).isEqualTo(GroupAuditFailure.NO_CHANGE);

        GroupAdminServiceTest duplicate = new GroupAdminServiceTest();
        duplicate.locked("Sales");
        when(duplicate.groups.findIdByNameKey("総務部")).thenReturn(Optional.of(GROUP + 1));
        assertThat(duplicate.service.rename(ACTOR, ORIGIN, GROUP, "総務部"))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.NAME_DUPLICATE));
        assertThat(duplicate.onlyEvent().detail()).isEqualTo(new GroupAuditDetail.Rename("Sales", "総務部"));
        duplicate.failurePublishedAfterRollback();

        GroupAdminServiceTest caseOnly = new GroupAdminServiceTest();
        caseOnly.locked("Sales");
        when(caseOnly.groups.findIdByNameKey("sales")).thenReturn(Optional.of(GROUP));
        when(caseOnly.store.renameGroup(any(), any(), eq(NOW))).thenReturn(new StoreOutcome.Done<>(null));
        assertThat(caseOnly.service.rename(ACTOR, ORIGIN, GROUP, "SALES")).isEqualTo(new GroupChangeResult.Done());
        InOrder order = inOrder(caseOnly.barrier, caseOnly.store);
        order.verify(caseOnly.barrier).beforeLock(GroupOperation.RENAME, GROUP);
        order.verify(caseOnly.store).lockGroup(GROUP);
        order.verify(caseOnly.barrier).afterLock(GroupOperation.RENAME, GROUP);
        order.verify(caseOnly.barrier).afterCheck(GroupOperation.RENAME, "sales");
        order.verify(caseOnly.store).renameGroup(any(), any(), eq(NOW));
        order.verify(caseOnly.barrier).afterWrite(GroupOperation.RENAME, "sales");
        assertThat(caseOnly.onlyEvent().detail()).isEqualTo(new GroupAuditDetail.Rename("Sales", "SALES"));
        assertThat(caseOnly.onlyEvent().succeeded()).isTrue();
    }

    @Test
    @DisplayName("a rename that hits the unique violation is a duplicate with the before and requested names")
    void renameViolation() {
        locked("Sales");
        when(groups.findIdByNameKey("総務部")).thenReturn(Optional.empty());
        when(store.renameGroup(any(), any(), any())).thenReturn(new StoreOutcome.NameTaken<>());

        assertThat(service.rename(ACTOR, ORIGIN, GROUP, "総務部"))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.NAME_DUPLICATE));
        failurePublishedAfterRollback();
        assertThat(onlyEvent().detail()).isEqualTo(new GroupAuditDetail.Rename("Sales", "総務部"));
        assertThat(onlyEvent().targetGroupId()).isEqualTo(GROUP);
    }

    @Test
    @DisplayName(
            "a deletion always asks the guard and is refused with the remaining counts while members or roles remain")
    void deleteInUse() {
        locked("営業部");
        when(members.countByGroup(GROUP)).thenReturn(3L);
        when(guard.canDelete(GROUP)).thenReturn(new DeletionDecision.Allowed());
        assertThat(service.delete(ACTOR, ORIGIN, GROUP)).isEqualTo(new GroupChangeResult.InUse(3, 0));
        verify(guard).canDelete(GROUP);
        assertThat(onlyEvent().detail()).isEqualTo(new GroupAuditDetail.InUse("営業部", 3, 0));
        assertThat(onlyEvent().failure()).isEqualTo(GroupAuditFailure.GROUP_IN_USE);
        failurePublishedAfterRollback();
        verify(store, never()).deleteGroup(any());

        GroupAdminServiceTest blocked = new GroupAdminServiceTest();
        blocked.locked("営業部");
        when(blocked.members.countByGroup(GROUP)).thenReturn(0L);
        when(blocked.guard.canDelete(GROUP)).thenReturn(new DeletionDecision.Blocked(2));
        assertThat(blocked.service.delete(ACTOR, ORIGIN, GROUP)).isEqualTo(new GroupChangeResult.InUse(0, 2));

        GroupAdminServiceTest both = new GroupAdminServiceTest();
        both.locked("営業部");
        when(both.members.countByGroup(GROUP)).thenReturn(1L);
        when(both.guard.canDelete(GROUP)).thenReturn(new DeletionDecision.Blocked(4));
        assertThat(both.service.delete(ACTOR, ORIGIN, GROUP)).isEqualTo(new GroupChangeResult.InUse(1, 4));
    }

    @Test
    @DisplayName(
            "an empty group is deleted with a success event, and a foreign key violation becomes GROUP_IN_USE recounted")
    void deleteEmptyAndReferenced() {
        locked("営業部");
        when(members.countByGroup(GROUP)).thenReturn(0L);
        when(guard.canDelete(GROUP)).thenReturn(new DeletionDecision.Allowed());
        when(store.deleteGroup(any())).thenReturn(new StoreOutcome.Done<>(null));
        assertThat(service.delete(ACTOR, ORIGIN, GROUP)).isEqualTo(new GroupChangeResult.Done());
        assertThat(onlyEvent())
                .isEqualTo(GroupAuditEvent.succeeded(
                        GroupOperation.DELETE, ACTOR, GROUP, null, new GroupAuditDetail.Name("営業部"), NOW, ORIGIN));
        verify(barrier).afterWrite(GroupOperation.DELETE, String.valueOf(GROUP));

        GroupAdminServiceTest referenced = new GroupAdminServiceTest();
        referenced.locked("営業部");
        when(referenced.members.countByGroup(GROUP)).thenReturn(0L, 2L);
        when(referenced.guard.canDelete(GROUP)).thenReturn(new DeletionDecision.Allowed());
        when(referenced.guard.assignedRoleCounts(Set.of(GROUP))).thenReturn(Map.of(GROUP, 0));
        when(referenced.store.deleteGroup(any())).thenReturn(new StoreOutcome.Referenced<>());
        assertThat(referenced.service.delete(ACTOR, ORIGIN, GROUP)).isEqualTo(new GroupChangeResult.InUse(2, 0));
        referenced.failurePublishedAfterRollback();
        assertThat(referenced.onlyEvent().detail()).isEqualTo(new GroupAuditDetail.InUse("営業部", 2, 0));
    }

    @Test
    @DisplayName("adding a member checks the group, then the user, then the membership, in this order")
    void addMemberOrder() {
        when(store.lockGroup(GROUP)).thenReturn(new StoreOutcome.GroupMissing<>());
        assertThat(service.addMember(ACTOR, ORIGIN, GROUP, USER))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.GROUP_NOT_FOUND));
        verifyNoInteractions(userAccounts);
        assertThat(onlyEvent())
                .isEqualTo(GroupAuditEvent.failed(
                        GroupOperation.ADD_MEMBER,
                        ACTOR,
                        GROUP,
                        USER,
                        GroupAuditFailure.GROUP_NOT_FOUND,
                        null,
                        NOW,
                        ORIGIN));

        GroupAdminServiceTest noUser = new GroupAdminServiceTest();
        noUser.locked("営業部");
        when(noUser.userAccounts.findById(USER)).thenReturn(Optional.empty());
        assertThat(noUser.service.addMember(ACTOR, ORIGIN, GROUP, USER))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.USER_NOT_FOUND));
        verify(noUser.members, never()).isMember(anyLong(), anyLong());
        assertThat(noUser.onlyEvent().detail()).isNull();
        assertThat(noUser.onlyEvent().targetUserId()).isEqualTo(USER);
        noUser.failurePublishedAfterRollback();

        GroupAdminServiceTest already = new GroupAdminServiceTest();
        already.locked("営業部");
        already.userExists();
        when(already.members.isMember(GROUP, USER)).thenReturn(true);
        assertThat(already.service.addMember(ACTOR, ORIGIN, GROUP, USER))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.NO_CHANGE));
        assertThat(already.onlyEvent().detail()).isEqualTo(new GroupAuditDetail.Membership("営業部"));
        verify(already.store, never()).insertMember(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("adding a member is audited on success, and store violations are read per operation")
    void addMemberOutcomes() {
        locked("営業部");
        userExists();
        when(store.insertMember(GROUP, USER, NOW)).thenReturn(new StoreOutcome.Done<>(null));
        assertThat(service.addMember(ACTOR, ORIGIN, GROUP, USER)).isEqualTo(new GroupChangeResult.Done());
        assertThat(onlyEvent())
                .isEqualTo(GroupAuditEvent.succeeded(
                        GroupOperation.ADD_MEMBER,
                        ACTOR,
                        GROUP,
                        USER,
                        new GroupAuditDetail.Membership("営業部"),
                        NOW,
                        ORIGIN));
        verify(barrier).afterCheck(GroupOperation.ADD_MEMBER, GROUP + ":" + USER);
        verify(barrier).afterWrite(GroupOperation.ADD_MEMBER, GROUP + ":" + USER);

        GroupAdminServiceTest primaryKey = new GroupAdminServiceTest();
        primaryKey.locked("営業部");
        primaryKey.userExists();
        when(primaryKey.store.insertMember(anyLong(), anyLong(), any())).thenReturn(new StoreOutcome.AlreadyMember<>());
        assertThat(primaryKey.service.addMember(ACTOR, ORIGIN, GROUP, USER))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.NO_CHANGE));
        primaryKey.failurePublishedAfterRollback();
        assertThat(primaryKey.onlyEvent().failure()).isEqualTo(GroupAuditFailure.NO_CHANGE);

        GroupAdminServiceTest foreignKey = new GroupAdminServiceTest();
        foreignKey.locked("営業部");
        foreignKey.userExists();
        when(foreignKey.store.insertMember(anyLong(), anyLong(), any())).thenReturn(new StoreOutcome.Referenced<>());
        assertThat(foreignKey.service.addMember(ACTOR, ORIGIN, GROUP, USER))
                .as("メンバーの追加の外部キーの違反は GROUP_NOT_FOUND に読み替える（D-6）")
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.GROUP_NOT_FOUND));
        foreignKey.failurePublishedAfterRollback();
        assertThat(foreignKey.onlyEvent().detail()).isNull();
        assertThat(foreignKey.onlyEvent().failure()).isEqualTo(GroupAuditFailure.GROUP_NOT_FOUND);
    }

    @Test
    @DisplayName("removing a non-member is no change, removing a member is audited after the write point")
    void removeMember() {
        locked("営業部");
        when(store.deleteMember(GROUP, USER)).thenReturn(new StoreOutcome.Done<>(false));
        assertThat(service.removeMember(ACTOR, ORIGIN, GROUP, USER))
                .isEqualTo(new GroupChangeResult.Rejected(GroupRejection.NO_CHANGE));
        failurePublishedAfterRollback();
        verify(barrier, never()).afterWrite(any(), any());

        GroupAdminServiceTest removed = new GroupAdminServiceTest();
        removed.locked("営業部");
        when(removed.store.deleteMember(GROUP, USER)).thenReturn(new StoreOutcome.Done<>(true));
        assertThat(removed.service.removeMember(ACTOR, ORIGIN, GROUP, USER)).isEqualTo(new GroupChangeResult.Done());
        assertThat(removed.onlyEvent())
                .isEqualTo(GroupAuditEvent.succeeded(
                        GroupOperation.REMOVE_MEMBER,
                        ACTOR,
                        GROUP,
                        USER,
                        new GroupAuditDetail.Membership("営業部"),
                        NOW,
                        ORIGIN));
        verify(removed.barrier).afterWrite(GroupOperation.REMOVE_MEMBER, GROUP + ":" + USER);
        assertThat(removed.recording.commits()).isEqualTo(1);
    }

    @Test
    @DisplayName("the list reads the page, member counts and role counts in one read-only transaction without events")
    void list() {
        assertThat(service.list("0")).isEqualTo(new GroupListResult.InvalidPage());
        assertThat(service.list("x")).isEqualTo(new GroupListResult.InvalidPage());
        when(groups.countAll()).thenReturn(2L);
        when(groups.findPage(any()))
                .thenReturn(List.of(new GroupRowView(GROUP, "営業部"), new GroupRowView(GROUP + 1, "総務部")));
        when(members.countByGroups(Set.of(GROUP, GROUP + 1))).thenReturn(List.of(new GroupMemberCount(GROUP, 3)));
        when(guard.assignedRoleCounts(Set.of(GROUP, GROUP + 1))).thenReturn(Map.of(GROUP, 0, GROUP + 1, 2));

        GroupListResult result = service.list(null);

        assertThat(result)
                .isEqualTo(new GroupListResult.Listed(new GroupListResult.Page(
                        List.of(
                                new GroupListResult.Row(GROUP, "営業部", 3, 0),
                                new GroupListResult.Row(GROUP + 1, "総務部", 0, 2)),
                        1,
                        20,
                        2)));
        assertThat(recording.definitions())
                .singleElement()
                .satisfies(definition -> assertThat(definition.isReadOnly()).isTrue());
        assertThat(events).isEmpty();

        GroupAdminServiceTest beyond = new GroupAdminServiceTest();
        when(beyond.groups.countAll()).thenReturn(2L);
        assertThat(beyond.service.list("2"))
                .isEqualTo(new GroupListResult.Listed(new GroupListResult.Page(List.of(), 2, 20, 2)));
        verify(beyond.groups, never()).findPage(any());
        verifyNoInteractions(beyond.guard);
    }

    @Test
    @DisplayName("the detail lists the members in the stored order with names and emails hidden in the string form")
    void detail() {
        when(groups.findView(GROUP)).thenReturn(Optional.of(new GroupRowView(GROUP, "営業部")));
        when(members.findMembers(GROUP))
                .thenReturn(List.of(new GroupMemberRow(USER + 1, NOW), new GroupMemberRow(USER, NOW.plusSeconds(1))));
        when(userAccounts.findSummariesByIds(Set.of(USER, USER + 1)))
                .thenReturn(List.of(
                        new UserAdminSummary(USER, "taro@example.com", "営業 太郎", Language.JA, false, true, NOW),
                        new UserAdminSummary(USER + 1, "hanako@example.com", "営業 花子", Language.EN, true, false, NOW)));

        GroupDetailResult result = service.detail(GROUP);

        assertThat(result)
                .isEqualTo(new GroupDetailResult.Found(
                        GROUP,
                        "営業部",
                        List.of(
                                new GroupMember(USER + 1, "営業 花子", "hanako@example.com", false, NOW),
                                new GroupMember(USER, "営業 太郎", "taro@example.com", true, NOW.plusSeconds(1)))));
        assertThat(result.toString()).doesNotContain("taro@example.com").doesNotContain("営業 花子");
        assertThat(events).isEmpty();

        GroupAdminServiceTest missing = new GroupAdminServiceTest();
        when(missing.groups.findView(GROUP)).thenReturn(Optional.empty());
        assertThat(missing.service.detail(GROUP)).isEqualTo(new GroupDetailResult.NotFound());
        verifyNoInteractions(missing.userAccounts);
        assertThat(missing.events).isEmpty();
    }
}
