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

import cherry.mastersmith.common.paging.Paging;
import cherry.mastersmith.group.domain.Group;
import cherry.mastersmith.group.domain.GroupAuditDetail;
import cherry.mastersmith.group.domain.GroupAuditEvent;
import cherry.mastersmith.group.domain.GroupMember;
import cherry.mastersmith.group.domain.GroupName;
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
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserAdminSummary;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * グループの管理の業務処理（Intent 261004-role-menu の U3、{@code logical-components.md} の L3、FS の 2.1〜2.7）。
 *
 * <p>このクラスには {@code @Transactional} を付けず、トランザクションを {@link TransactionTemplate} で作る（{@code UserAdminService}
 * と同じ形）。変える操作は、store を呼ぶ1つ目のトランザクションを {@link GroupStoreTransactions} で組み、入力の判定 → グループの行の
 * 排他 → 待ち合わせの口 → 業務の判定 → 書き込みと flush → 成功の出来事の順に進む（BR5.1）。排他の前に書き込みは無い。
 *
 * <ul>
 *   <li>成功の出来事は1つ目のトランザクションの中で出し、確定の後に {@code audit} が記録する（BR8.1）
 *   <li>業務の拒否（書き込みの前の判定と、違反の読み替え）は、1つ目を巻き戻した後に、2つ目の {@link TransactionTemplate}（書き込み
 *       なし）で失敗の出来事だけを出す（BR5.7・BR8.2、{@code reliability-design.md} 2.3）
 *   <li>待ちの上限切れ（{@code GROUP_BUSY}）・入力の誤り・読み取りは出来事を出さない（BR5.2・BR8.3）
 * </ul>
 *
 * <p>入れ子の REQUIRES_NEW は使わない。一覧と詳細は読み取りだけの1つのトランザクションで読む。個人に関する値は伏せる型のまま受け渡し、
 * 業務のログは出さない（上限切れの WARN は store が出す）。操作した人の管理者の印は、要求の入口の決まりだけで確かめる（BR5.6）。
 */
@Service
public class GroupAdminService {

    private final GroupStoreTransactions transactions;

    private final GroupRepository groups;

    private final GroupMemberRepository members;

    private final UserAccountService userAccounts;

    private final GroupDeletionGuard deletionGuard;

    private final GroupBarrier barrier;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private final TransactionTemplate failureTransaction;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param transactions store を呼ぶ1つ目のトランザクションの入口
     * @param groups グループの表の読み取り
     * @param members メンバーの表の読み取り
     * @param userAccounts UserAccount の口（利用者の有無とまとめて読む口）
     * @param deletionGuard role が実装する問う口
     * @param barrier 待ち合わせの口
     * @param eventPublisher 監査の出来事の知らせ
     * @param clock 時計
     * @param transactionManager トランザクションの管理
     */
    public GroupAdminService(
            GroupStoreTransactions transactions,
            GroupRepository groups,
            GroupMemberRepository members,
            UserAccountService userAccounts,
            GroupDeletionGuard deletionGuard,
            GroupBarrier barrier,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.transactions = transactions;
        this.groups = groups;
        this.members = members;
        this.userAccounts = userAccounts;
        this.deletionGuard = deletionGuard;
        this.barrier = barrier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * グループを作る（FS の 2.1、BR1.1〜BR1.4・BR5.2・BR5.5・BR5.7）。
     *
     * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値）
     * @param origin 要求の送り手の情報
     * @param rawName 要求の名前（正規化の前の値）
     * @return 結果
     */
    public GroupCreateResult create(long actorUserId, RequestOrigin origin, String rawName) {
        Objects.requireNonNull(origin, "origin");
        if (!(GroupName.parse(rawName) instanceof GroupNameValidation.Valid(GroupName name))) {
            return new GroupCreateResult.Invalid(invalidReason(rawName));
        }
        GroupAuditDetail detail = new GroupAuditDetail.Name(name.value());
        FirstStep<GroupView> step = transactions.inFirst(store -> {
            if (groups.findIdByNameKey(name.key()).isPresent()) {
                return new FirstStep.Rejected<>(GroupRejection.NAME_DUPLICATE, detail);
            }
            barrier.afterCheck(GroupOperation.CREATE, name.key());
            StoreOutcome<Group> outcome = store.insertGroup(name, clock.instant());
            if (!(outcome instanceof StoreOutcome.Done<Group>(Group group))) {
                return new FirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(GroupOperation.CREATE, name.key());
            eventPublisher.publishEvent(GroupAuditEvent.succeeded(
                    GroupOperation.CREATE, actorUserId, group.getGroupId(), null, detail, clock.instant(), origin));
            return new FirstStep.Done<>(
                    new GroupView(group.getGroupId(), group.getName(), group.getCreatedAt(), group.getUpdatedAt()));
        });
        return switch (step) {
            case FirstStep.Done<GroupView>(GroupView view) -> new GroupCreateResult.Created(view);
            case FirstStep.Rejected<GroupView>(GroupRejection rejection, GroupAuditDetail rejected) -> {
                publishFailure(GroupOperation.CREATE, actorUserId, null, null, rejection, rejected, origin);
                yield new GroupCreateResult.Rejected(rejection);
            }
            case FirstStep.Store<GroupView>(StoreOutcome<?> outcome, GroupAuditDetail violated) ->
                switch (outcome) {
                    case StoreOutcome.Busy<?> _ -> new GroupCreateResult.Busy();
                    case StoreOutcome.NameTaken<?> _ -> {
                        publishFailure(
                                GroupOperation.CREATE,
                                actorUserId,
                                null,
                                null,
                                GroupRejection.NAME_DUPLICATE,
                                violated,
                                origin);
                        yield new GroupCreateResult.Rejected(GroupRejection.NAME_DUPLICATE);
                    }
                    default -> throw unexpected(GroupOperation.CREATE, outcome);
                };
        };
    }

    /**
     * グループの名前を変える（FS の 2.2、BR1.4・BR1.5・BR5.1・BR5.2・BR5.5・BR5.7）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param groupId グループの ID（要求の値のまま）
     * @param rawName 要求の名前（正規化の前の値）
     * @return 結果
     */
    public GroupChangeResult rename(long actorUserId, RequestOrigin origin, long groupId, String rawName) {
        Objects.requireNonNull(origin, "origin");
        if (!(GroupName.parse(rawName) instanceof GroupNameValidation.Valid(GroupName name))) {
            return new GroupChangeResult.Invalid(invalidReason(rawName));
        }
        GroupOperation operation = GroupOperation.RENAME;
        FirstStep<Void> step = transactions.inFirst(store -> withLockedGroup(store, operation, groupId, group -> {
            String before = group.getName();
            if (name.sameAs(before)) {
                return new FirstStep.Rejected<>(GroupRejection.NO_CHANGE, new GroupAuditDetail.Name(before));
            }
            GroupAuditDetail detail = new GroupAuditDetail.Rename(before, name.value());
            Optional<Long> sameKey = groups.findIdByNameKey(name.key());
            if (sameKey.isPresent() && sameKey.get() != groupId) {
                return new FirstStep.Rejected<>(GroupRejection.NAME_DUPLICATE, detail);
            }
            barrier.afterCheck(operation, name.key());
            StoreOutcome<Group> outcome = store.renameGroup(group, name, clock.instant());
            if (!outcome.isDone()) {
                return new FirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(operation, name.key());
            return done(operation, actorUserId, groupId, null, detail, origin);
        }));
        return changeResult(step, operation, actorUserId, groupId, null, origin);
    }

    /**
     * グループを消す（FS の 2.3、BR4.1〜BR4.4・BR5.1・BR5.4・BR5.7）。メンバーが残っていても問う口に必ず聞く（BR4.3）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param groupId グループの ID（要求の値のまま）
     * @return 結果
     */
    public GroupChangeResult delete(long actorUserId, RequestOrigin origin, long groupId) {
        Objects.requireNonNull(origin, "origin");
        GroupOperation operation = GroupOperation.DELETE;
        FirstStep<Void> step = transactions.inFirst(store -> withLockedGroup(store, operation, groupId, group -> {
            long memberCount = members.countByGroup(groupId);
            DeletionDecision decision = deletionGuard.canDelete(groupId);
            if (memberCount > 0 || decision instanceof DeletionDecision.Blocked) {
                return new FirstStep.Rejected<>(
                        GroupRejection.IN_USE,
                        new GroupAuditDetail.InUse(
                                group.getName(), Math.toIntExact(memberCount), decision.assignedRoles()));
            }
            GroupAuditDetail detail = new GroupAuditDetail.Name(group.getName());
            StoreOutcome<Void> outcome = store.deleteGroup(group);
            if (!outcome.isDone()) {
                return new FirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(operation, String.valueOf(groupId));
            return done(operation, actorUserId, groupId, null, detail, origin);
        }));
        return changeResult(step, operation, actorUserId, groupId, null, origin);
    }

    /**
     * メンバーを足す（FS の 2.4、BR3.1〜BR3.3・BR3.5・BR5.1・BR5.4・BR5.5・BR5.7）。判定の順はグループの有無 → 利用者の有無 →
     * すでにメンバーか（BR3.5）。停止中の利用者も足せる（BR3.2）。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param groupId グループの ID（要求の値のまま）
     * @param userId 足す利用者 ID（要求の値のまま）
     * @return 結果
     */
    public GroupChangeResult addMember(long actorUserId, RequestOrigin origin, long groupId, long userId) {
        Objects.requireNonNull(origin, "origin");
        GroupOperation operation = GroupOperation.ADD_MEMBER;
        String key = memberKey(groupId, userId);
        FirstStep<Void> step = transactions.inFirst(store -> withLockedGroup(store, operation, groupId, group -> {
            if (userAccounts.findById(userId).isEmpty()) {
                return new FirstStep.Rejected<>(GroupRejection.USER_NOT_FOUND, null);
            }
            GroupAuditDetail detail = new GroupAuditDetail.Membership(group.getName());
            if (members.isMember(groupId, userId)) {
                return new FirstStep.Rejected<>(GroupRejection.NO_CHANGE, detail);
            }
            barrier.afterCheck(operation, key);
            StoreOutcome<Void> outcome = store.insertMember(groupId, userId, clock.instant());
            if (!outcome.isDone()) {
                return new FirstStep.Store<>(outcome, detail);
            }
            barrier.afterWrite(operation, key);
            return done(operation, actorUserId, groupId, userId, detail, origin);
        }));
        return changeResult(step, operation, actorUserId, groupId, userId, origin);
    }

    /**
     * メンバーを外す（FS の 2.5、BR3.4〜BR3.6・BR5.1）。メンバーでなければ（存在しない利用者を含む）変えるものが無い。
     *
     * @param actorUserId 操作した管理者の利用者 ID
     * @param origin 要求の送り手の情報
     * @param groupId グループの ID（要求の値のまま）
     * @param userId 外す利用者 ID（要求の値のまま）
     * @return 結果
     */
    public GroupChangeResult removeMember(long actorUserId, RequestOrigin origin, long groupId, long userId) {
        Objects.requireNonNull(origin, "origin");
        GroupOperation operation = GroupOperation.REMOVE_MEMBER;
        String key = memberKey(groupId, userId);
        FirstStep<Void> step = transactions.inFirst(store -> withLockedGroup(store, operation, groupId, group -> {
            GroupAuditDetail detail = new GroupAuditDetail.Membership(group.getName());
            StoreOutcome<Boolean> outcome = store.deleteMember(groupId, userId);
            if (!(outcome instanceof StoreOutcome.Done<Boolean>(Boolean removed))) {
                return new FirstStep.Store<>(outcome, detail);
            }
            if (!removed) {
                return new FirstStep.Rejected<>(GroupRejection.NO_CHANGE, detail);
            }
            barrier.afterWrite(operation, key);
            return done(operation, actorUserId, groupId, userId, detail, origin);
        }));
        return changeResult(step, operation, actorUserId, groupId, userId, origin);
    }

    /**
     * グループの一覧の1ページを読む（FS の 2.6、BR7.1〜BR7.3）。全体の件数・ページの行・メンバーの数・ロールの数を読み取りだけの1つの
     * トランザクションで読む。監査の出来事は出さない。
     *
     * @param rawPage 要求の page（無ければ null）
     * @return 結果
     */
    public GroupListResult list(String rawPage) {
        OptionalInt parsed = Paging.parsePage(rawPage);
        if (parsed.isEmpty()) {
            return new GroupListResult.InvalidPage();
        }
        int page = parsed.getAsInt();
        GroupListResult.Page result = readOnly.execute(status -> readPage(page));
        return new GroupListResult.Listed(Objects.requireNonNull(result, "result"));
    }

    /**
     * グループの詳細を読む（FS の 2.7、BR7.4・BR9.1）。メンバーは足した順に全件で、氏名・メールアドレス・停止を {@code user} の口で
     * まとめて読む。監査の出来事は出さない。
     *
     * @param groupId グループの ID（要求の値のまま）
     * @return 結果
     */
    public GroupDetailResult detail(long groupId) {
        return Objects.requireNonNull(readOnly.execute(status -> readDetail(groupId)), "result");
    }

    /** グループの行を排他し、取れたら処理を続ける。行が無ければ拒否、上限切れは store の結果のまま返す。 */
    private FirstStep<Void> withLockedGroup(
            GroupStore store, GroupOperation operation, long groupId, Function<Group, FirstStep<Void>> work) {
        barrier.beforeLock(operation, groupId);
        StoreOutcome<Group> lock = store.lockGroup(groupId);
        return switch (lock) {
            case StoreOutcome.Done<Group>(Group group) -> {
                barrier.afterLock(operation, groupId);
                yield work.apply(group);
            }
            case StoreOutcome.GroupMissing<Group> _ -> new FirstStep.Rejected<>(GroupRejection.GROUP_NOT_FOUND, null);
            default -> new FirstStep.Store<>(lock, null);
        };
    }

    /** 成功の出来事を1つ目のトランザクションの中で出し、成功の結果を返す。 */
    private FirstStep<Void> done(
            GroupOperation operation,
            long actorUserId,
            long groupId,
            Long userId,
            GroupAuditDetail detail,
            RequestOrigin origin) {
        eventPublisher.publishEvent(
                GroupAuditEvent.succeeded(operation, actorUserId, groupId, userId, detail, clock.instant(), origin));
        return new FirstStep.Done<>(null);
    }

    /**
     * 1つ目の結果を、変える操作の結果に変える。拒否と違反の読み替えは2つ目のトランザクションで失敗の出来事を出す。外部キーの違反は
     * 操作ごとに読み替える（削除なら {@code GROUP_IN_USE}、メンバーの追加なら {@code GROUP_NOT_FOUND}。BR5.4、計画の D-6）。
     */
    private GroupChangeResult changeResult(
            FirstStep<Void> step,
            GroupOperation operation,
            long actorUserId,
            long groupId,
            Long userId,
            RequestOrigin origin) {
        return switch (step) {
            case FirstStep.Done<Void> _ -> new GroupChangeResult.Done();
            case FirstStep.Rejected<Void>(GroupRejection rejection, GroupAuditDetail detail) -> {
                publishFailure(operation, actorUserId, groupId, userId, rejection, detail, origin);
                yield rejection == GroupRejection.IN_USE
                        ? inUse((GroupAuditDetail.InUse) detail)
                        : new GroupChangeResult.Rejected(rejection);
            }
            case FirstStep.Store<Void>(StoreOutcome<?> outcome, GroupAuditDetail detail) ->
                switch (outcome) {
                    case StoreOutcome.Busy<?> _ -> new GroupChangeResult.Busy();
                    case StoreOutcome.NameTaken<?> _
                    when operation == GroupOperation.RENAME -> {
                        publishFailure(
                                operation, actorUserId, groupId, null, GroupRejection.NAME_DUPLICATE, detail, origin);
                        yield new GroupChangeResult.Rejected(GroupRejection.NAME_DUPLICATE);
                    }
                    case StoreOutcome.AlreadyMember<?> _
                    when operation == GroupOperation.ADD_MEMBER -> {
                        publishFailure(
                                operation, actorUserId, groupId, userId, GroupRejection.NO_CHANGE, detail, origin);
                        yield new GroupChangeResult.Rejected(GroupRejection.NO_CHANGE);
                    }
                    case StoreOutcome.Referenced<?> _
                    when operation == GroupOperation.ADD_MEMBER -> {
                        publishFailure(
                                operation, actorUserId, groupId, userId, GroupRejection.GROUP_NOT_FOUND, null, origin);
                        yield new GroupChangeResult.Rejected(GroupRejection.GROUP_NOT_FOUND);
                    }
                    case StoreOutcome.Referenced<?> _
                    when operation == GroupOperation.DELETE ->
                        referencedOnDelete(actorUserId, groupId, (GroupAuditDetail.Name) detail, origin);
                    default -> throw unexpected(operation, outcome);
                };
        };
    }

    /**
     * 削除の外部キーの違反（最後の守り）を {@code GROUP_IN_USE} に読み替える。残りの数は2つ目のトランザクション（書き込みなし）で
     * 数え直し、同じトランザクションで失敗の出来事を出す（1つ目は違反で使えないため）。
     */
    private GroupChangeResult referencedOnDelete(
            long actorUserId, long groupId, GroupAuditDetail.Name named, RequestOrigin origin) {
        GroupAuditDetail.InUse detail = Objects.requireNonNull(failureTransaction.execute(status -> {
            long memberCount = members.countByGroup(groupId);
            int assignedRoles =
                    deletionGuard.assignedRoleCounts(Set.of(groupId)).getOrDefault(groupId, 0);
            GroupAuditDetail.InUse inUse =
                    new GroupAuditDetail.InUse(named.name(), Math.toIntExact(memberCount), assignedRoles);
            eventPublisher.publishEvent(failedEvent(
                    GroupOperation.DELETE, actorUserId, groupId, null, GroupRejection.IN_USE, inUse, origin));
            return inUse;
        }));
        return inUse(detail);
    }

    private static GroupChangeResult.InUse inUse(GroupAuditDetail.InUse detail) {
        return new GroupChangeResult.InUse(detail.members(), detail.assignedRoles());
    }

    /** 1つ目を巻き戻した後に、2つ目のトランザクション（書き込みなし）で失敗の出来事だけを出す（BR5.7・BR8.2）。 */
    private void publishFailure(
            GroupOperation operation,
            long actorUserId,
            Long groupId,
            Long userId,
            GroupRejection rejection,
            GroupAuditDetail detail,
            RequestOrigin origin) {
        GroupAuditEvent event = failedEvent(operation, actorUserId, groupId, userId, rejection, detail, origin);
        failureTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private GroupAuditEvent failedEvent(
            GroupOperation operation,
            long actorUserId,
            Long groupId,
            Long userId,
            GroupRejection rejection,
            GroupAuditDetail detail,
            RequestOrigin origin) {
        return GroupAuditEvent.failed(
                operation, actorUserId, groupId, userId, rejection.auditFailure(), detail, clock.instant(), origin);
    }

    private GroupListResult.Page readPage(int page) {
        long total = groups.countAll();
        long offset = Paging.offsetOf(page);
        if (offset >= total) {
            return new GroupListResult.Page(List.of(), page, Paging.PAGE_SIZE, total);
        }
        List<GroupRowView> views = groups.findPage(PageRequest.of(page - 1, Paging.PAGE_SIZE));
        Set<Long> ids = views.stream().map(GroupRowView::groupId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Long> memberCounts = members.countByGroups(ids).stream()
                .collect(Collectors.toMap(GroupMemberCount::groupId, GroupMemberCount::members));
        Map<Long, Integer> roleCounts = deletionGuard.assignedRoleCounts(Set.copyOf(ids));
        List<GroupListResult.Row> rows = views.stream()
                .map(view -> new GroupListResult.Row(
                        view.groupId(),
                        view.name(),
                        memberCounts.getOrDefault(view.groupId(), 0L),
                        roleCounts.getOrDefault(view.groupId(), 0)))
                .toList();
        return new GroupListResult.Page(rows, page, Paging.PAGE_SIZE, total);
    }

    private GroupDetailResult readDetail(long groupId) {
        Optional<GroupRowView> view = groups.findView(groupId);
        if (view.isEmpty()) {
            return new GroupDetailResult.NotFound();
        }
        List<GroupMemberRow> rows = members.findMembers(groupId);
        Set<Long> userIds = rows.stream().map(GroupMemberRow::userId).collect(Collectors.toSet());
        Map<Long, UserAdminSummary> summaries = userAccounts.findSummariesByIds(userIds).stream()
                .collect(Collectors.toMap(UserAdminSummary::userId, Function.identity()));
        List<GroupMember> memberList = rows.stream()
                .map(row -> {
                    UserAdminSummary summary = summaries.get(row.userId());
                    if (summary == null) {
                        // 利用者への外部キーがあるため、メンバーの利用者は必ずいる（いなければ想定外）。
                        throw new IllegalStateException("メンバーの利用者がいません: userId=" + row.userId());
                    }
                    return new GroupMember(
                            row.userId(), summary.displayName(), summary.email(), summary.suspended(), row.addedAt());
                })
                .toList();
        return new GroupDetailResult.Found(view.get().groupId(), view.get().name(), memberList);
    }

    /** 名前の入力の誤りの理由を返す（{@link GroupName#parse(String)} が誤りを返したときだけ呼ぶ）。 */
    private static GroupNameValidation.Reason invalidReason(String rawName) {
        return ((GroupNameValidation.Invalid) GroupName.parse(rawName)).reason();
    }

    private static String memberKey(long groupId, long userId) {
        return groupId + ":" + userId;
    }

    private static IllegalStateException unexpected(GroupOperation operation, StoreOutcome<?> outcome) {
        return new IllegalStateException(
                operation + " の結果として想定外です: " + outcome.getClass().getSimpleName());
    }
}
