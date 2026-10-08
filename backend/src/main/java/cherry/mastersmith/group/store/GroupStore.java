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
package cherry.mastersmith.group.store;

import cherry.mastersmith.group.domain.Group;
import cherry.mastersmith.group.domain.GroupMembership;
import cherry.mastersmith.group.domain.GroupName;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * グループの行の排他と、違反と待ちの上限切れを起こしうる書き込みと flush（{@code logical-components.md} の L2、
 * {@code reliability-design.md} 2.2、{@code security-design.md} 4.1、BR5.1・BR5.2・BR5.4・BR5.5・BR9.3）。
 *
 * <p>方法は「排他（{@link #lockGroup(long)}）」と「書き込みと flush（作成・名前の変更・削除・メンバーの追加と外し）」に分ける（計画の
 * D-5）。業務の判定と待ち合わせの口は呼び出し元（{@code group.service}）が間に挟む。どの方法も呼び出し元のトランザクションの中で使い、
 * 例外をこのクラスの中で受けて {@link StoreOutcome} に変える（{@link StoreFailureClassifier}）。違反を起こしうる書き込みはその場で
 * DB へ送る（flush）ため、違反は確定の前に起きる（BR5.5）。
 *
 * <p>行の排他の待ちの上限は既存の排他と同じ 3,000 ミリ秒（問い合わせのヒント。Hibernate は {@code for update wait 3} として出す）。
 * 一意の鍵・主キーの書き込みの待ちはヒントが効かず H2 の既定（約 2 秒）で切れる（捨ての試しの T1〜T3。設定は変えない）。問い合わせは
 * 定数の JPQL と名前の付いた引数だけで組む（NFR1.9）。{@link EntityManager} はコンストラクターで受ける。
 */
@Component
public class GroupStore {

    /** 行の排他の待ちの上限（ミリ秒。既存の排他と同じ値）。 */
    static final int LOCK_TIMEOUT_MILLIS = 3000;

    /** グループの行の排他の種類（WARN の {@code lockKind}）。 */
    public static final String GROUP_ROW = "GROUP_ROW";

    /** 名前の鍵の待ちの種類（WARN の {@code lockKind}）。 */
    public static final String GROUP_NAME_KEY = "GROUP_NAME_KEY";

    /** メンバーの主キーの待ちの種類（WARN の {@code lockKind}）。 */
    public static final String GROUP_MEMBER_KEY = "GROUP_MEMBER_KEY";

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupStore.class);

    private static final String LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";

    private static final String LOCK_GROUP = "select g from UserGroup g where g.groupId = :groupId";

    private static final String DELETE_MEMBER =
            "delete from GroupMembership m where m.groupId = :groupId and m.userId = :userId";

    private final EntityManager entityManager;

    /**
     * 作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public GroupStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * グループの行を排他して読む（BR5.1・BR5.3）。
     *
     * @param groupId グループの ID（0 以下も「無い」として扱う）
     * @return 取れたらグループ、行が無ければ {@link StoreOutcome.GroupMissing}、上限切れなら {@link StoreOutcome.Busy}
     *     （{@value #GROUP_ROW}）
     */
    public StoreOutcome<Group> lockGroup(long groupId) {
        try {
            List<Group> rows = entityManager
                    .createQuery(LOCK_GROUP, Group.class)
                    .setParameter("groupId", groupId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint(LOCK_TIMEOUT_HINT, LOCK_TIMEOUT_MILLIS)
                    .getResultList();
            return rows.isEmpty() ? new StoreOutcome.GroupMissing<>() : new StoreOutcome.Done<>(rows.getFirst());
        } catch (PersistenceException e) {
            return StoreFailureClassifier.toOutcome(e, GROUP_ROW, LOGGER);
        }
    }

    /**
     * グループを足し、その場で DB へ送る（BR5.5）。
     *
     * @param name 名前
     * @param now 今の日時
     * @return 足したグループ（ID が振られた値）、名前の鍵の違反なら {@link StoreOutcome.NameTaken}、鍵の待ちの上限切れなら
     *     {@link StoreOutcome.Busy}（{@value #GROUP_NAME_KEY}）
     */
    public StoreOutcome<Group> insertGroup(GroupName name, Instant now) {
        Group group = new Group(Objects.requireNonNull(name, "name"), now);
        try {
            entityManager.persist(group);
            entityManager.flush();
            return new StoreOutcome.Done<>(group);
        } catch (PersistenceException e) {
            return StoreFailureClassifier.toOutcome(e, GROUP_NAME_KEY, LOGGER);
        }
    }

    /**
     * 排他したグループの名前を変え、その場で DB へ送る（BR5.5）。
     *
     * @param group 排他したグループ（{@link #lockGroup(long)} の結果）
     * @param name 新しい名前
     * @param now 今の日時
     * @return 成功ならグループ、名前の鍵の違反なら {@link StoreOutcome.NameTaken}、鍵の待ちの上限切れなら {@link StoreOutcome.Busy}
     *     （{@value #GROUP_NAME_KEY}）
     */
    public StoreOutcome<Group> renameGroup(Group group, GroupName name, Instant now) {
        Objects.requireNonNull(group, "group");
        try {
            group.rename(name, now);
            entityManager.flush();
            return new StoreOutcome.Done<>(group);
        } catch (PersistenceException e) {
            return StoreFailureClassifier.toOutcome(e, GROUP_NAME_KEY, LOGGER);
        }
    }

    /**
     * 排他したグループを消し、その場で DB へ送る。メンバーが残っていれば外部キーの違反（最後の守り。BR5.4）。
     *
     * @param group 排他したグループ（{@link #lockGroup(long)} の結果）
     * @return 成功なら値なし、外部キーの違反なら {@link StoreOutcome.Referenced}、待ちの上限切れなら {@link StoreOutcome.Busy}
     *     （{@value #GROUP_ROW}）
     */
    public StoreOutcome<Void> deleteGroup(Group group) {
        Objects.requireNonNull(group, "group");
        try {
            entityManager.remove(group);
            entityManager.flush();
            return new StoreOutcome.Done<>(null);
        } catch (PersistenceException e) {
            return StoreFailureClassifier.toOutcome(e, GROUP_ROW, LOGGER);
        }
    }

    /**
     * メンバーの行を足し、その場で DB へ送る（BR5.5）。
     *
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @param now 今の日時（足した日時）
     * @return 成功なら値なし、主キーの違反なら {@link StoreOutcome.AlreadyMember}、グループへの外部キーの違反なら
     *     {@link StoreOutcome.Referenced}、主キーの待ちの上限切れなら {@link StoreOutcome.Busy}（{@value #GROUP_MEMBER_KEY}）
     */
    public StoreOutcome<Void> insertMember(long groupId, long userId, Instant now) {
        try {
            entityManager.persist(new GroupMembership(groupId, userId, now));
            entityManager.flush();
            return new StoreOutcome.Done<>(null);
        } catch (PersistenceException e) {
            return StoreFailureClassifier.toOutcome(e, GROUP_MEMBER_KEY, LOGGER);
        }
    }

    /**
     * メンバーの行を消す（BR3.4）。
     *
     * @param groupId グループの ID
     * @param userId 利用者 ID
     * @return 消したら true・メンバーでなければ false、待ちの上限切れなら {@link StoreOutcome.Busy}（{@value #GROUP_MEMBER_KEY}）
     */
    public StoreOutcome<Boolean> deleteMember(long groupId, long userId) {
        try {
            int deleted = entityManager
                    .createQuery(DELETE_MEMBER)
                    .setParameter("groupId", groupId)
                    .setParameter("userId", userId)
                    .executeUpdate();
            return new StoreOutcome.Done<>(deleted > 0);
        } catch (PersistenceException e) {
            return StoreFailureClassifier.toOutcome(e, GROUP_MEMBER_KEY, LOGGER);
        }
    }
}
