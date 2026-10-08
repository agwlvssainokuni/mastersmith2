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
package cherry.mastersmith.role.store;

import cherry.mastersmith.role.domain.PermissionSetting;
import cherry.mastersmith.role.domain.PermissionSettingId;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.domain.Role;
import cherry.mastersmith.role.domain.RoleName;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ロールの行の排他と、違反と待ちの上限切れを起こしうる書き込みと flush（{@code logical-components.md} の L2、
 * {@code reliability-design.md} 2.2・2.3、{@code security-design.md} 4.1、BR8.1・BR8.3〜BR8.5・BR12.3）。
 *
 * <p>方法は「排他（{@link #lockRole(long)}）」と「書き込みと flush（作成・名前の変更・削除・設定の書き込み・消す）」に分ける。業務の判定と
 * 待ち合わせの口は呼び出し元（{@code role.service}）が間に挟む。どの方法も呼び出し元のトランザクションの中で使い、例外をこのクラスの
 * 中で受けて {@link RoleStoreOutcome} に変える（{@link RoleStoreClassifier}）。違反を起こしうる書き込みはその場で DB へ送る（flush）
 * ため、違反は確定の前に起きる。
 *
 * <p>行の排他の待ちの上限は既存の排他と同じ 3,000 ミリ秒（問い合わせのヒント）。一意の鍵の書き込みの待ちはヒントが効かず H2 の既定
 * （約 2 秒）で切れる（{@code reliability-design.md} 2.1。設定は変えない）。問い合わせは定数の JPQL と名前の付いた引数だけで組む
 * （NFR1.11）。{@link EntityManager} はコンストラクターで受ける。
 */
@Component
public class RoleStore {

    /** 行の排他の待ちの上限（ミリ秒。既存の排他と同じ値）。 */
    static final int LOCK_TIMEOUT_MILLIS = 3000;

    /** ロールの行の排他の種類（WARN の {@code lockKind}）。 */
    public static final String ROLE_ROW = "ROLE_ROW";

    /** 名前の鍵の待ちの種類（WARN の {@code lockKind}）。 */
    public static final String ROLE_NAME_KEY = "ROLE_NAME_KEY";

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleStore.class);

    private static final String LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";

    private static final String LOCK_ROLE = "select r from Role r where r.roleId = :roleId";

    private static final String DELETE_SETTINGS = "delete from PermissionSetting p where p.roleId = :roleId";

    private static final String DELETE_SETTING = "delete from PermissionSetting p where p.roleId = :roleId"
            + " and p.schemaName = :schemaName and p.tableName = :tableName and p.columnName = :columnName";

    private final EntityManager entityManager;

    /**
     * 作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public RoleStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * ロールの行を排他して読む（BR8.1・BR8.3）。
     *
     * @param roleId ロールの ID（0 以下も「無い」として扱う）
     * @return 取れたらロール、行が無ければ {@link RoleStoreOutcome.RoleMissing}、上限切れなら {@link RoleStoreOutcome.Busy}
     *     （{@value #ROLE_ROW}）
     */
    public RoleStoreOutcome<Role> lockRole(long roleId) {
        try {
            List<Role> rows = entityManager
                    .createQuery(LOCK_ROLE, Role.class)
                    .setParameter("roleId", roleId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint(LOCK_TIMEOUT_HINT, LOCK_TIMEOUT_MILLIS)
                    .getResultList();
            return rows.isEmpty() ? new RoleStoreOutcome.RoleMissing<>() : new RoleStoreOutcome.Done<>(rows.getFirst());
        } catch (PersistenceException e) {
            return RoleStoreClassifier.toOutcome(e, ROLE_ROW, LOGGER);
        }
    }

    /**
     * ロールを足し、その場で DB へ送る（BR8.5）。
     *
     * @param name 名前
     * @param now 今の日時
     * @return 足したロール（ID が振られた値）、名前の鍵の違反なら {@link RoleStoreOutcome.NameTaken}、鍵の待ちの上限切れなら
     *     {@link RoleStoreOutcome.Busy}（{@value #ROLE_NAME_KEY}）
     */
    public RoleStoreOutcome<Role> insertRole(RoleName name, Instant now) {
        Role role = new Role(Objects.requireNonNull(name, "name"), now);
        try {
            entityManager.persist(role);
            entityManager.flush();
            return new RoleStoreOutcome.Done<>(role);
        } catch (PersistenceException e) {
            return RoleStoreClassifier.toOutcome(e, ROLE_NAME_KEY, LOGGER);
        }
    }

    /**
     * 排他したロールの名前を変え、その場で DB へ送る（BR8.5）。
     *
     * @param role 排他したロール（{@link #lockRole(long)} の結果）
     * @param name 新しい名前
     * @param now 今の日時
     * @return 成功ならロール、名前の鍵の違反なら {@link RoleStoreOutcome.NameTaken}、鍵の待ちの上限切れなら
     *     {@link RoleStoreOutcome.Busy}（{@value #ROLE_NAME_KEY}）
     */
    public RoleStoreOutcome<Role> renameRole(Role role, RoleName name, Instant now) {
        Objects.requireNonNull(role, "role");
        try {
            role.rename(name, now);
            entityManager.flush();
            return new RoleStoreOutcome.Done<>(role);
        } catch (PersistenceException e) {
            return RoleStoreClassifier.toOutcome(e, ROLE_NAME_KEY, LOGGER);
        }
    }

    /**
     * 排他したロールを、その権限の設定から順に消し、その場で DB へ送る（BR3.3。作業ロールの保存の削除は B5 で足す）。
     *
     * @param role 排他したロール（{@link #lockRole(long)} の結果）
     * @return 成功なら値なし、外部キーの違反なら {@link RoleStoreOutcome.Referenced}、待ちの上限切れなら
     *     {@link RoleStoreOutcome.Busy}（{@value #ROLE_ROW}）
     */
    public RoleStoreOutcome<Void> deleteRole(Role role) {
        Objects.requireNonNull(role, "role");
        try {
            entityManager
                    .createQuery(DELETE_SETTINGS)
                    .setParameter("roleId", role.getRoleId())
                    .executeUpdate();
            entityManager.remove(role);
            entityManager.flush();
            return new RoleStoreOutcome.Done<>(null);
        } catch (PersistenceException e) {
            return RoleStoreClassifier.toOutcome(e, ROLE_ROW, LOGGER);
        }
    }

    /**
     * 排他したロールの設定を、対象ごとの後の値に書き換え、ロールの更新の日時を書き、その場で DB へ送る（BR4.2・BR4.4）。後の値が
     * すべて設定なしの対象は行を消し、行の無い対象は行を足し、ある対象は値を書き換える。
     *
     * @param role 排他したロール（{@link #lockRole(long)} の結果）
     * @param afters 対象ごとの後の値（変わる対象だけ）
     * @param now 今の日時
     * @return 成功なら値なし、外部キーの違反なら {@link RoleStoreOutcome.Referenced}、待ちの上限切れなら
     *     {@link RoleStoreOutcome.Busy}（{@value #ROLE_ROW}）
     */
    public RoleStoreOutcome<Void> writePermissions(
            Role role, Map<PermissionTarget, PermissionValues> afters, Instant now) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(afters, "afters");
        long roleId = role.getRoleId();
        try {
            for (Map.Entry<PermissionTarget, PermissionValues> entry : afters.entrySet()) {
                PermissionSetting current =
                        entityManager.find(PermissionSetting.class, new PermissionSettingId(roleId, entry.getKey()));
                PermissionValues after = entry.getValue();
                if (after.isEmpty()) {
                    if (current != null) {
                        entityManager.remove(current);
                    }
                } else if (current == null) {
                    entityManager.persist(new PermissionSetting(roleId, entry.getKey(), after, now));
                } else {
                    current.update(after, now);
                }
            }
            role.touch(now);
            entityManager.flush();
            return new RoleStoreOutcome.Done<>(null);
        } catch (PersistenceException e) {
            return RoleStoreClassifier.toOutcome(e, ROLE_ROW, LOGGER);
        }
    }

    /**
     * 排他したロールの指定の対象の設定の行を消し、ロールの更新の日時を書き、その場で DB へ送る（BR4.7）。
     *
     * @param role 排他したロール（{@link #lockRole(long)} の結果）
     * @param targets 消す対象
     * @param now 今の日時
     * @return 消した行の数、待ちの上限切れなら {@link RoleStoreOutcome.Busy}（{@value #ROLE_ROW}）
     */
    public RoleStoreOutcome<Integer> clearPermissions(Role role, List<PermissionTarget> targets, Instant now) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(targets, "targets");
        try {
            int deleted = 0;
            for (PermissionTarget target : targets) {
                deleted += entityManager
                        .createQuery(DELETE_SETTING)
                        .setParameter("roleId", role.getRoleId())
                        .setParameter("schemaName", target.schemaName())
                        .setParameter("tableName", target.storedTableName())
                        .setParameter("columnName", target.storedColumnName())
                        .executeUpdate();
            }
            if (deleted > 0) {
                role.touch(now);
                entityManager.flush();
            }
            return new RoleStoreOutcome.Done<>(deleted);
        } catch (PersistenceException e) {
            return RoleStoreClassifier.toOutcome(e, ROLE_ROW, LOGGER);
        }
    }
}
