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
package cherry.mastersmith.role.domain;

import java.util.List;
import java.util.Objects;

/**
 * 監査の detail に入れる中身（BR11.5・BR11.6・BR11.8、契約 C10。{@code entities.md} の RoleAuditDetail）。
 *
 * <p>形ごとにキーを決めた型で、{@code audit} がこの型から決めたキーだけの JSON を作る。ロールの名前・対象の名前・権限の値・数だけを
 * 持ち、メールアドレス・氏名・パスワード・トークン・ハッシュ値は型に無い。自由な対応表や任意の文字列の連結は受けない。B4 の形は
 * {@link Name}・{@link Rename}・{@link InUse}・{@link PermissionChanges}・{@link PermissionChangesSummary}、B5 の形は割り当ての
 * {@link Assignment} と作業ロールの切り替えの {@link WorkRoleSwitch}。受け渡し（B6）の形はその Bolt で足す。
 */
public sealed interface RoleAuditDetail
        permits RoleAuditDetail.Name,
                RoleAuditDetail.Rename,
                RoleAuditDetail.InUse,
                RoleAuditDetail.PermissionChanges,
                RoleAuditDetail.PermissionChangesSummary,
                RoleAuditDetail.Assignment,
                RoleAuditDetail.WorkRoleSwitch {

    /**
     * 作成・削除・名前の重なり・同じ名前への変更・権限の操作の拒否の detail。
     *
     * @param name ロールの名前
     */
    record Name(String name) implements RoleAuditDetail {

        /** 値が null でないことを確かめる。 */
        public Name {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * 名前の変更の detail（重なりで断った変更では、after に要求の名前を入れる）。
     *
     * @param before 変える前の名前
     * @param after 変えた後（要求）の名前
     */
    record Rename(String before, String after) implements RoleAuditDetail {

        /** 値が null でないことを確かめる。 */
        public Rename {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    /**
     * 割り当てが残る削除の拒否の detail（BR3.2 の直接の割り当ての数）。
     *
     * @param name ロールの名前
     * @param assignedUsers 利用者への直接の割り当ての数
     * @param assignedGroups グループへの割り当ての数
     */
    record InUse(String name, int assignedUsers, int assignedGroups) implements RoleAuditDetail {

        /** 値が null でなく、数が負でないことを確かめる。 */
        public InUse {
            Objects.requireNonNull(name, "name");
            if (assignedUsers < 0 || assignedGroups < 0) {
                throw new IllegalArgumentException("数は 0 以上です");
            }
        }
    }

    /**
     * 1つの対象の前後の値（BR11.6）。
     *
     * @param target 対象
     * @param before 前の値（すべて設定なしは行が無かった）
     * @param after 後の値（すべて設定なしは行を消した）
     */
    record PermissionChange(PermissionTarget target, PermissionValues before, PermissionValues after) {

        /** 値が null でないことを確かめる。 */
        public PermissionChange {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    /**
     * 権限の保存・消す操作の detail（変わった対象ごとの前後の値の全件。BR11.6）。
     *
     * @param roleName ロールの名前
     * @param changes 変わった対象（保存の要求の順）
     */
    record PermissionChanges(String roleName, List<PermissionChange> changes) implements RoleAuditDetail {

        /** 値を写して持つ。 */
        public PermissionChanges {
            Objects.requireNonNull(roleName, "roleName");
            changes = List.copyOf(changes);
        }

        /**
         * 先頭の何件かと変わった数の要約にする（全件の JSON が上限を超えるとき。BR11.5）。
         *
         * @param first 要約に入れる先頭の件数（0 以上。全件より多ければ全件）
         * @return 要約
         */
        public PermissionChangesSummary summarize(int first) {
            if (first < 0) {
                throw new IllegalArgumentException("件数は 0 以上です");
            }
            return new PermissionChangesSummary(
                    roleName, changes.size(), changes.subList(0, Math.min(first, changes.size())));
        }
    }

    /**
     * 権限の保存・消す操作の detail の要約（全件の JSON が 16,384 文字を超えるとき。BR11.5、計画の D-11）。
     *
     * @param roleName ロールの名前
     * @param changedCount 変わった対象の数
     * @param firstChanges 先頭の何件か
     */
    record PermissionChangesSummary(String roleName, int changedCount, List<PermissionChange> firstChanges)
            implements RoleAuditDetail {

        /** 値を写して持つ。 */
        public PermissionChangesSummary {
            Objects.requireNonNull(roleName, "roleName");
            firstChanges = List.copyOf(firstChanges);
            if (changedCount < firstChanges.size()) {
                throw new IllegalArgumentException("変わった数は先頭の件数以上です");
            }
        }
    }

    /**
     * 割り当て・外しと、その業務の拒否の detail（BR11.4。B5）。対象の利用者・グループは監査の行の対象の列に ID で入れ、ここには
     * ロールとグループの名前だけを持つ（利用者の氏名・メールアドレスは持たない。BR11.8）。
     *
     * @param roleName ロールの名前
     * @param groupName グループの名前（利用者への割り当て・相手のグループがいない拒否では null）
     */
    record Assignment(String roleName, String groupName) implements RoleAuditDetail {

        /** ロールの名前が null でないことを確かめる。 */
        public Assignment {
            Objects.requireNonNull(roleName, "roleName");
        }
    }

    /**
     * 作業ロールの切り替えの detail（BR7.6・BR7.7・BR11.4。B5）。有効な作業ロールと同じロールを明示で選び、読み替え中の保存を書き直した
     * ときは、前の作業ロールと選んだロールが同じになる（{@code storedBeforeRoleId} で書く前の保存が分かる）。
     *
     * @param fromRoleId 切り替えの前の有効な作業ロールの ID（無ければ null）
     * @param fromRoleName 切り替えの前の有効な作業ロールの名前（無ければ null）
     * @param toRoleName 選んだロールの名前
     * @param storedBeforeRoleId 書く前の保存のロールの ID（保存が無ければ null）
     */
    record WorkRoleSwitch(Long fromRoleId, String fromRoleName, String toRoleName, Long storedBeforeRoleId)
            implements RoleAuditDetail {

        /** 選んだロールの名前が null でなく、前の作業ロールの ID と名前がそろっていることを確かめる。 */
        public WorkRoleSwitch {
            Objects.requireNonNull(toRoleName, "toRoleName");
            if ((fromRoleId == null) != (fromRoleName == null)) {
                throw new IllegalArgumentException("前の作業ロールの ID と名前はそろえて持ちます");
            }
        }
    }
}
