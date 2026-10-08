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

import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.role.domain.RoleRejection;
import cherry.mastersmith.user.domain.FieldErrorReason;
import java.util.Objects;

/**
 * ロールの名前の変更・削除・権限の保存・消す操作の結果（FS の 2.2・2.3・2.6・2.7）。web の層が {@code switch} で場合を尽くして応答に
 * 変える（BR13.1）。文字列は種類と数だけ（D-5）。
 */
public sealed interface RoleChangeResult
        permits RoleChangeResult.Done,
                RoleChangeResult.InvalidName,
                RoleChangeResult.InvalidInput,
                RoleChangeResult.Rejected,
                RoleChangeResult.InUse,
                RoleChangeResult.Busy {

    /** 変えた（204）。 */
    record Done() implements RoleChangeResult {}

    /**
     * 名前の入力の誤り（400。名前の変更だけ。監査なし）。
     *
     * @param reason 理由
     */
    record InvalidName(RoleNameValidation.Reason reason) implements RoleChangeResult {

        /** 値が null でないことを確かめる。 */
        public InvalidName {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 権限の保存・消す操作の入力の誤り（400。監査なし。入れた値は持たない）。
     *
     * @param field 項目の名前（{@code entries[0].main} など）
     * @param reason 理由
     */
    record InvalidInput(String field, FieldErrorReason reason) implements RoleChangeResult {

        /** 値が null でないことを確かめる。 */
        public InvalidInput {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 業務の拒否（割り当てが残る削除を除く。監査に FAILURE）。
     *
     * @param rejection 理由（{@code IN_USE} は {@link InUse} で返す）
     */
    record Rejected(RoleRejection rejection) implements RoleChangeResult {

        /** 値が null でなく、使用中でないことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
            if (rejection == RoleRejection.IN_USE) {
                throw new IllegalArgumentException("割り当てが残る削除は InUse で返します");
            }
        }
    }

    /**
     * 割り当てが残る削除の拒否（409 {@code ROLE_IN_USE}、応答に残りの数。BR3.2。監査に FAILURE。B4 では割り当ての表が無く起きない）。
     *
     * @param assignedUsers 利用者への直接の割り当ての数
     * @param assignedGroups グループへの割り当ての数
     */
    record InUse(int assignedUsers, int assignedGroups) implements RoleChangeResult {

        /** 数が負でないことを確かめる。 */
        public InUse {
            if (assignedUsers < 0 || assignedGroups < 0) {
                throw new IllegalArgumentException("数は 0 以上です");
            }
        }
    }

    /** 排他の待ちの上限切れ（409 {@code ROLE_BUSY}。監査なし）。 */
    record Busy() implements RoleChangeResult {}
}
