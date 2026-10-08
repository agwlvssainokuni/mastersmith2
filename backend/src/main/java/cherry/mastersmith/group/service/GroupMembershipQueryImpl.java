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

import cherry.mastersmith.group.domain.Group;
import cherry.mastersmith.group.repository.GroupMemberPair;
import cherry.mastersmith.group.repository.GroupMemberRepository;
import cherry.mastersmith.group.repository.GroupRepository;
import cherry.mastersmith.group.store.GroupStore;
import cherry.mastersmith.group.store.StoreOutcome;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link GroupMembershipQuery} の実装（{@code logical-components.md} の L4、{@code performance-design.md} 1節）。
 *
 * <p>読み取りは読み取りだけの {@link TransactionTemplate} で1回ずつ行う（呼び出し元のトランザクションが有ればそれに入る）。排他は
 * {@code group.store} に任せ、呼び出し元のトランザクションの中でだけ行う。巻き戻しの印は呼び出し元が付ける。
 */
@Service
public class GroupMembershipQueryImpl implements GroupMembershipQuery {

    private final GroupRepository groups;

    private final GroupMemberRepository members;

    private final GroupStore store;

    private final TransactionTemplate readOnly;

    /**
     * 作る。
     *
     * @param groups グループの表の読み取り
     * @param members メンバーの表の読み取り
     * @param store グループの排他と書き込み
     * @param transactionManager トランザクションの管理
     */
    public GroupMembershipQueryImpl(
            GroupRepository groups,
            GroupMemberRepository members,
            GroupStore store,
            PlatformTransactionManager transactionManager) {
        this.groups = groups;
        this.members = members;
        this.store = store;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Set<Long> groupIdsOfUser(long userId) {
        List<Long> ids = readOnly.execute(status -> members.findGroupIdsOfUser(userId));
        return Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(ids, "ids")));
    }

    @Override
    public boolean exists(long groupId) {
        return Boolean.TRUE.equals(readOnly.execute(status -> groups.existsGroup(groupId)));
    }

    @Override
    public List<GroupSummary> summaries(Set<Long> groupIds) {
        Objects.requireNonNull(groupIds, "groupIds");
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return Objects.requireNonNull(
                readOnly.execute(status -> groups.findViews(Set.copyOf(groupIds)).stream()
                        .map(view -> new GroupSummary(view.groupId(), view.name()))
                        .toList()),
                "summaries");
    }

    @Override
    public GroupRowLock lockForAssignment(long groupId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("呼び出し元のトランザクションの中で呼んでください");
        }
        StoreOutcome<Group> outcome = store.lockGroup(groupId);
        return switch (outcome) {
            case StoreOutcome.Done<Group> _ -> new GroupRowLock.Locked(true);
            case StoreOutcome.GroupMissing<Group> _ -> new GroupRowLock.Locked(false);
            case StoreOutcome.Busy<Group> _ -> new GroupRowLock.Busy();
            case StoreOutcome.NameTaken<Group> _,
                    StoreOutcome.AlreadyMember<Group> _,
                    StoreOutcome.Referenced<Group> _ -> throw unexpected(outcome);
        };
    }

    @Override
    public Map<Long, Set<Long>> memberUserIds(Set<Long> groupIds) {
        Objects.requireNonNull(groupIds, "groupIds");
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        List<GroupMemberPair> pairs =
                Objects.requireNonNull(readOnly.execute(status -> members.findMemberPairs(Set.copyOf(groupIds))));
        Map<Long, Set<Long>> byGroup = new LinkedHashMap<>();
        for (GroupMemberPair pair : pairs) {
            Set<Long> users = byGroup.computeIfAbsent(pair.groupId(), id -> new LinkedHashSet<>());
            if (pair.userId() != null) {
                users.add(pair.userId());
            }
        }
        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        byGroup.forEach((groupId, users) -> result.put(groupId, Collections.unmodifiableSet(users)));
        return Collections.unmodifiableMap(result);
    }

    private static IllegalStateException unexpected(StoreOutcome<?> outcome) {
        return new IllegalStateException(
                "排他の読み取りの結果として想定外です: " + outcome.getClass().getSimpleName());
    }
}
