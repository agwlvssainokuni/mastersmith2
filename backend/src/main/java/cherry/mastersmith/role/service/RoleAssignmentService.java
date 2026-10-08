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

import cherry.mastersmith.group.service.GroupMembershipQuery;
import cherry.mastersmith.group.service.GroupRowLock;
import cherry.mastersmith.group.service.GroupSummary;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleAuditDetail;
import cherry.mastersmith.role.domain.RoleAuditEvent;
import cherry.mastersmith.role.domain.RoleOperation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.role.domain.RoleSource;
import cherry.mastersmith.role.domain.UserRoleView;
import cherry.mastersmith.role.domain.WorkRoleRef;
import cherry.mastersmith.role.repository.GroupAssignedRoleRow;
import cherry.mastersmith.role.repository.RoleAssignmentRepository;
import cherry.mastersmith.role.repository.RoleRepository;
import cherry.mastersmith.role.store.Referent;
import cherry.mastersmith.role.store.RoleStore;
import cherry.mastersmith.role.store.RoleStoreOutcome;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserAdminSummary;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ロールの割り当てと外し、割り当ての読み取りの業務処理（Intent 261004-role-menu の U4、{@code logical-components.md} の L3、FS の 2.8、
 * BR6・BR8.1・BR8.2・BR8.5・BR11・BR12.1）。
 *
 * <p>このクラスには {@code @Transactional} を付けず、トランザクションを {@link TransactionTemplate} で作る。変える操作は、store を呼ぶ
 * 1つ目のトランザクションを {@link RoleStoreTransactions} で組み、入力 → ロールの行の排他 → 待ち合わせの口 → 相手の有無（グループは
 * group の {@code lockForAssignment} でグループの行を排他。ロール → グループの順。BR8.2）→ すでに割り当て済み → 書き込みと flush → 成功の
 * 出来事の順に進む（BR6.9）。業務の拒否と違反の読み替えは、1つ目を巻き戻した後に2つ目のトランザクション（書き込みなし）で失敗の出来事を
 * 出す（計画の 4.3、13節 Q2: A）。待ちの上限切れは出来事を出さない（ロールの行と割り当ての主キーは {@code ROLE_BUSY}、グループの行は
 * {@code GROUP_BUSY}）。
 *
 * <p>割り当ての相手の利用者は {@code user.service} で有無だけを確かめ（招待中の人は行が無く {@code USER_NOT_FOUND}、停止中は受け付ける。
 * BR6.1）、操作する人の業務データの権限では縛らない（BR6.10）。読み取りの利用者の氏名・メールアドレスは伏せ字の型のまま受け渡す（BR12.1）。
 */
@Service
public class RoleAssignmentService {

    /** グループの行の待ちの上限切れを1つ目の結果に載せるときの排他の種類（WARN は group が出し済み）。 */
    static final String GROUP_ROW = "GROUP_ROW";

    private final RoleStoreTransactions transactions;

    private final RoleRepository roles;

    private final RoleAssignmentRepository assignments;

    private final UserAccountService userAccounts;

    private final GroupMembershipQuery groups;

    private final UserRoleReader userRoles;

    private final RoleBarrier barrier;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final TransactionTemplate failureTransaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param transactions store を呼ぶ1つ目のトランザクションの入口
     * @param roles ロールの表の読み取り
     * @param assignments 割り当ての表の読み取り
     * @param userAccounts UserAccount の口（利用者の有無とまとめて読む口）
     * @param groups group の読み取りと排他の口
     * @param userRoles 利用者のロールを決める読み取り
     * @param barrier 待ち合わせの口
     * @param eventPublisher 監査の出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public RoleAssignmentService(
            RoleStoreTransactions transactions,
            RoleRepository roles,
            RoleAssignmentRepository assignments,
            UserAccountService userAccounts,
            GroupMembershipQuery groups,
            UserRoleReader userRoles,
            RoleBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.transactions = transactions;
        this.roles = roles;
        this.assignments = assignments;
        this.userAccounts = userAccounts;
        this.groups = groups;
        this.userRoles = userRoles;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * ロールを利用者に割り当てる（FS の 2.8、BR6.1〜BR6.3・BR6.9）。判定の順は ロールの有無 → 利用者の有無 → すでに割り当て済み。
     *
     * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値）
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま。0 以下は無いロール）
     * @param userId 相手の利用者 ID（要求の値のまま）
     * @return 結果
     */
    public RoleAssignmentResult assignUser(long actorUserId, RequestOrigin origin, long roleId, long userId) {
        Objects.requireNonNull(origin, "origin");
        Target target = Target.user(userId);
        RoleOperation operation = RoleOperation.ASSIGN;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            RoleAuditDetail detail = new RoleAuditDetail.Assignment(role.getName(), null);
            if (userAccounts.findById(userId).isEmpty()) {
                return new RoleFirstStep.Rejected<>(RoleRejection.USER_NOT_FOUND, detail);
            }
            if (assignments.existsUserAssignment(roleId, userId)) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, detail);
            }
            barrier.afterCheck(operation, target.key(roleId));
            RoleStoreOutcome<Void> outcome = store.assignToUser(role, userId, clock.instant());
            if (!outcome.isDone()) {
                return new RoleFirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(operation, target.key(roleId));
            return done(operation, actorUserId, roleId, target, detail, origin);
        }));
        return result(step, operation, actorUserId, roleId, target, origin);
    }

    /**
     * ロールをグループに割り当てる（FS の 2.8、BR6.2・BR6.3・BR6.6・BR6.9・BR8.2）。ロールの行の後にグループの行を排他する。判定の順は
     * ロールの有無 → グループの有無 → すでに割り当て済み。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま）
     * @param groupId 相手のグループの ID（要求の値のまま）
     * @return 結果
     */
    public RoleAssignmentResult assignGroup(long actorUserId, RequestOrigin origin, long roleId, long groupId) {
        Objects.requireNonNull(origin, "origin");
        Target target = Target.group(groupId);
        RoleOperation operation = RoleOperation.ASSIGN;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            switch (groups.lockForAssignment(groupId)) {
                case GroupRowLock.Busy _ -> {
                    return new RoleFirstStep.Store<>(new RoleStoreOutcome.Busy<Void>(GROUP_ROW), null);
                }
                case GroupRowLock.Locked(boolean exists)
                when !exists -> {
                    return new RoleFirstStep.Rejected<>(
                            RoleRejection.GROUP_NOT_FOUND, new RoleAuditDetail.Assignment(role.getName(), null));
                }
                case GroupRowLock.Locked _ -> {
                    // 続ける
                }
            }
            RoleAuditDetail detail = new RoleAuditDetail.Assignment(role.getName(), groupName(groupId));
            if (assignments.existsGroupAssignment(roleId, groupId)) {
                return new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, detail);
            }
            barrier.afterCheck(operation, target.key(roleId));
            RoleStoreOutcome<Void> outcome = store.assignToGroup(role, groupId, clock.instant());
            if (!outcome.isDone()) {
                return new RoleFirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(operation, target.key(roleId));
            return done(operation, actorUserId, roleId, target, detail, origin);
        }));
        return result(step, operation, actorUserId, roleId, target, origin);
    }

    /**
     * 利用者への直接の割り当てを外す（FS の 2.8、BR6.4）。割り当てていない組（存在しない利用者を含む）は {@code ROLE_NO_CHANGE}。作業ロールの
     * 保存は書き換えない（BR7.3・BR7.4）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま）
     * @param userId 相手の利用者 ID（要求の値のまま）
     * @return 結果
     */
    public RoleAssignmentResult unassignUser(long actorUserId, RequestOrigin origin, long roleId, long userId) {
        Objects.requireNonNull(origin, "origin");
        Target target = Target.user(userId);
        RoleOperation operation = RoleOperation.UNASSIGN;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            RoleAuditDetail detail = new RoleAuditDetail.Assignment(role.getName(), null);
            return removed(
                    store.unassignFromUser(role, userId), operation, actorUserId, roleId, target, detail, origin);
        }));
        return result(step, operation, actorUserId, roleId, target, origin);
    }

    /**
     * グループへの割り当てを外す（FS の 2.8、BR6.4・BR6.6・BR8.2）。ロールの行の後にグループの行を排他する。グループがいなくても続け、組の行が
     * 無いため {@code ROLE_NO_CHANGE} になる。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param roleId ロールの ID（要求の値のまま）
     * @param groupId 相手のグループの ID（要求の値のまま）
     * @return 結果
     */
    public RoleAssignmentResult unassignGroup(long actorUserId, RequestOrigin origin, long roleId, long groupId) {
        Objects.requireNonNull(origin, "origin");
        Target target = Target.group(groupId);
        RoleOperation operation = RoleOperation.UNASSIGN;
        RoleFirstStep<Void> step = transactions.inFirst(store -> withLockedRole(store, operation, roleId, role -> {
            boolean exists;
            switch (groups.lockForAssignment(groupId)) {
                case GroupRowLock.Busy _ -> {
                    return new RoleFirstStep.Store<>(new RoleStoreOutcome.Busy<Void>(GROUP_ROW), null);
                }
                case GroupRowLock.Locked(boolean found) -> exists = found;
            }
            RoleAuditDetail detail = new RoleAuditDetail.Assignment(role.getName(), exists ? groupName(groupId) : null);
            return removed(
                    store.unassignFromGroup(role, groupId), operation, actorUserId, roleId, target, detail, origin);
        }));
        return result(step, operation, actorUserId, roleId, target, origin);
    }

    /**
     * ロールの割り当ての一覧を読む（FS の 2.8 の読み取り、BR6.5・BR6.11・BR12.1）。直接の割り当ての利用者と、そのロールを持つグループの
     * メンバー（{@code memberUserIds} でまとめて1回）を合わせ、利用者ごとに出どころを並べる。監査の出来事は出さない。
     *
     * @param roleId ロールの ID（要求の値のまま）
     * @return 結果
     */
    public RoleAssignmentsResult assignments(long roleId) {
        return Objects.requireNonNull(
                readOnly.execute(status -> {
                    if (!roles.existsRole(roleId)) {
                        return new RoleAssignmentsResult.RoleNotFound();
                    }
                    Map<Long, List<RoleSource>> sources = new TreeMap<>();
                    for (Long userId : assignments.findUserIdsOfRole(roleId)) {
                        sources.computeIfAbsent(userId, id -> new ArrayList<>()).add(new RoleSource.Direct());
                    }
                    Set<Long> groupIds = new TreeSet<>(assignments.findGroupIdsOfRole(roleId));
                    List<GroupSummary> groupSummaries = groups.summaries(groupIds);
                    Map<Long, String> groupNames = new LinkedHashMap<>();
                    groupSummaries.forEach(group -> groupNames.put(group.groupId(), group.name()));
                    Map<Long, Set<Long>> members = groups.memberUserIds(groupIds);
                    for (Map.Entry<Long, String> group : groupNames.entrySet()) {
                        for (Long userId : new TreeSet<>(members.getOrDefault(group.getKey(), Set.of()))) {
                            sources.computeIfAbsent(userId, id -> new ArrayList<>())
                                    .add(new RoleSource.Group(group.getKey(), group.getValue()));
                        }
                    }
                    List<RoleAssignmentsResult.AssignedUser> users = new ArrayList<>();
                    for (UserAdminSummary user : userAccounts.findSummariesByIds(sources.keySet())) {
                        users.add(new RoleAssignmentsResult.AssignedUser(user, sources.get(user.userId())));
                    }
                    return new RoleAssignmentsResult.Found(users, groupSummaries);
                }),
                "result");
    }

    /**
     * グループに割り当てたロールを読む（FS の 2.8 の読み取り）。監査の出来事は出さない。
     *
     * @param groupId グループの ID（要求の値のまま）
     * @return 結果
     */
    public GroupRolesResult rolesOfGroup(long groupId) {
        return Objects.requireNonNull(
                readOnly.execute(status -> {
                    if (!groups.exists(groupId)) {
                        return new GroupRolesResult.GroupNotFound();
                    }
                    List<RoleRef> refs = assignments.findRolesOfGroups(Set.of(groupId)).stream()
                            .map(row -> new RoleRef(row.roleId(), row.name()))
                            .toList();
                    return new GroupRolesResult.Found(refs);
                }),
                "result");
    }

    /**
     * 利用者のロールと出どころを読む（FS の 2.8 の読み取り、BR6.5）。招待中の人は利用者の行が無く {@code USER_NOT_FOUND}。監査の出来事は
     * 出さない。
     *
     * @param userId 利用者 ID（要求の値のまま）
     * @return 結果
     */
    public UserRolesResult rolesOfUser(long userId) {
        return Objects.requireNonNull(
                readOnly.execute(status -> {
                    if (userAccounts.findById(userId).isEmpty()) {
                        return new UserRolesResult.UserNotFound();
                    }
                    UserRoles read = userRoles.read(userId);
                    Set<Long> groupIds = new LinkedHashSet<>();
                    read.viaGroups().forEach(row -> groupIds.add(row.groupId()));
                    Map<Long, String> groupNames = new LinkedHashMap<>();
                    groups.summaries(groupIds).forEach(group -> groupNames.put(group.groupId(), group.name()));
                    Set<Long> direct = new LinkedHashSet<>();
                    read.direct().forEach(row -> direct.add(row.roleId()));
                    List<UserRoleView> views = new ArrayList<>();
                    for (WorkRoleRef role : read.roles()) {
                        List<RoleSource> roleSources = new ArrayList<>();
                        if (direct.contains(role.roleId())) {
                            roleSources.add(new RoleSource.Direct());
                        }
                        for (GroupAssignedRoleRow row : read.viaGroups()) {
                            String name = groupNames.get(row.groupId());
                            if (row.roleId() == role.roleId() && name != null) {
                                roleSources.add(new RoleSource.Group(row.groupId(), name));
                            }
                        }
                        if (!roleSources.isEmpty()) {
                            views.add(new UserRoleView(role.roleId(), role.name(), roleSources));
                        }
                    }
                    return new UserRolesResult.Found(views);
                }),
                "result");
    }

    /** 外しの書き込みの結果を1つ目の結果にする（消した行が無ければ変えるものが無い）。 */
    private RoleFirstStep<Void> removed(
            RoleStoreOutcome<Boolean> outcome,
            RoleOperation operation,
            long actorUserId,
            long roleId,
            Target target,
            RoleAuditDetail detail,
            RequestOrigin origin) {
        return switch (outcome) {
            case RoleStoreOutcome.Done<Boolean>(Boolean deleted)
            when Boolean.TRUE.equals(deleted) -> done(operation, actorUserId, roleId, target, detail, origin);
            case RoleStoreOutcome.Done<Boolean> _ -> new RoleFirstStep.Rejected<>(RoleRejection.NO_CHANGE, detail);
            default -> new RoleFirstStep.Store<>(outcome, detail);
        };
    }

    /** ロールの行を排他し、取れたら処理を続ける。行が無ければ拒否、上限切れは store の結果のまま返す。 */
    private RoleFirstStep<Void> withLockedRole(
            RoleStore store, RoleOperation operation, long roleId, Function<Role, RoleFirstStep<Void>> work) {
        barrier.beforeLock(operation, roleId);
        RoleStoreOutcome<Role> lock = store.lockRole(roleId);
        return switch (lock) {
            case RoleStoreOutcome.Done<Role>(Role role) -> {
                barrier.afterLock(operation, roleId);
                yield work.apply(role);
            }
            case RoleStoreOutcome.RoleMissing<Role> _ ->
                new RoleFirstStep.Rejected<>(RoleRejection.ROLE_NOT_FOUND, null);
            default -> new RoleFirstStep.Store<>(lock, null);
        };
    }

    /** 成功の出来事を1つ目のトランザクションの中で出し、成功の結果を返す。 */
    private RoleFirstStep<Void> done(
            RoleOperation operation,
            long actorUserId,
            long roleId,
            Target target,
            RoleAuditDetail detail,
            RequestOrigin origin) {
        eventPublisher.publishEvent(RoleAuditEvent.succeeded(
                operation, actorUserId, roleId, target.userId(), target.groupId(), detail, clock.instant(), origin));
        return new RoleFirstStep.Done<>(null);
    }

    /**
     * 1つ目の結果を、割り当て・外しの結果に変える。拒否と違反の読み替えは2つ目のトランザクションで失敗の出来事を出す（計画の 4.3）。
     * 割り当ての主キーの違反は {@code ROLE_NO_CHANGE}、外部キーの違反は相手ごとの {@code NOT_FOUND} に読み替える（どれも行の排他と事前の
     * 判定のため API からは届かない最後の守り。BR8.5）。
     */
    private RoleAssignmentResult result(
            RoleFirstStep<Void> step,
            RoleOperation operation,
            long actorUserId,
            long roleId,
            Target target,
            RequestOrigin origin) {
        return switch (step) {
            case RoleFirstStep.Done<Void> _ -> new RoleAssignmentResult.Done();
            case RoleFirstStep.Rejected<Void>(RoleRejection rejection, RoleAuditDetail detail) ->
                rejected(operation, actorUserId, roleId, target, rejection, detail, origin);
            case RoleFirstStep.Store<Void>(RoleStoreOutcome<?> outcome, RoleAuditDetail detail) ->
                switch (outcome) {
                    case RoleStoreOutcome.Busy<?>(String lockKind)
                    when GROUP_ROW.equals(lockKind) -> new RoleAssignmentResult.GroupBusy();
                    case RoleStoreOutcome.Busy<?> _ -> new RoleAssignmentResult.Busy();
                    case RoleStoreOutcome.AlreadyAssigned<?> _ ->
                        rejected(operation, actorUserId, roleId, target, RoleRejection.NO_CHANGE, detail, origin);
                    case RoleStoreOutcome.Referenced<?>(Referent who)
                    when who == Referent.ROLE ->
                        rejected(operation, actorUserId, roleId, target, RoleRejection.ROLE_NOT_FOUND, null, origin);
                    case RoleStoreOutcome.Referenced<?>(Referent who)
                    when who == Referent.USER ->
                        rejected(operation, actorUserId, roleId, target, RoleRejection.USER_NOT_FOUND, detail, origin);
                    case RoleStoreOutcome.Referenced<?>(Referent who)
                    when who == Referent.GROUP ->
                        rejected(
                                operation,
                                actorUserId,
                                roleId,
                                target,
                                RoleRejection.GROUP_NOT_FOUND,
                                detail instanceof RoleAuditDetail.Assignment(String roleName, String _)
                                        ? new RoleAuditDetail.Assignment(roleName, null)
                                        : detail,
                                origin);
                    default -> throw unexpected(operation, outcome);
                };
        };
    }

    /** 1つ目を巻き戻した後に、2つ目のトランザクション（書き込みなし）で失敗の出来事だけを出す（BR8.5・BR11.2）。 */
    private RoleAssignmentResult rejected(
            RoleOperation operation,
            long actorUserId,
            long roleId,
            Target target,
            RoleRejection rejection,
            RoleAuditDetail detail,
            RequestOrigin origin) {
        RoleAuditEvent event = RoleAuditEvent.failed(
                operation,
                actorUserId,
                roleId,
                target.userId(),
                target.groupId(),
                rejection.auditFailure(),
                rejection == RoleRejection.ROLE_NOT_FOUND ? null : detail,
                clock.instant(),
                origin);
        failureTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        return new RoleAssignmentResult.Rejected(rejection);
    }

    /** グループの名前を読む（排他の後。いなければ null）。 */
    private String groupName(long groupId) {
        return groups.summaries(Set.of(groupId)).stream()
                .map(GroupSummary::name)
                .findFirst()
                .orElse(null);
    }

    private static IllegalStateException unexpected(RoleOperation operation, RoleStoreOutcome<?> outcome) {
        return new IllegalStateException(operation + " の結果として想定外です: " + outcome);
    }

    /**
     * 割り当ての相手（利用者かグループのちょうど一方）。
     *
     * @param userId 利用者 ID（グループのときは null）
     * @param groupId グループの ID（利用者のときは null）
     */
    record Target(Long userId, Long groupId) {

        static Target user(long userId) {
            return new Target(userId, null);
        }

        static Target group(long groupId) {
            return new Target(null, groupId);
        }

        /** 待ち合わせの口の鍵（{@code <ロールの ID>:U<利用者 ID>}・{@code <ロールの ID>:G<グループの ID>}。計画の 4.4）。 */
        String key(long roleId) {
            return userId != null ? roleId + ":U" + userId : roleId + ":G" + groupId;
        }
    }
}
