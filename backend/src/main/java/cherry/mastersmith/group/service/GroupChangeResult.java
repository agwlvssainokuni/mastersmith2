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

import cherry.mastersmith.group.domain.GroupNameValidation;
import cherry.mastersmith.group.domain.GroupRejection;
import java.util.Objects;

/**
 * グループの名前の変更・削除・メンバーの追加と外しの結果（FS の 2.2〜2.5）。web の層が {@code switch} で場合を尽くして応答に変える
 * （BR10.1）。
 */
public sealed interface GroupChangeResult
        permits GroupChangeResult.Done,
                GroupChangeResult.Invalid,
                GroupChangeResult.Rejected,
                GroupChangeResult.InUse,
                GroupChangeResult.Busy {

    /** 変えた（204）。 */
    record Done() implements GroupChangeResult {}

    /**
     * 名前の入力の誤り（400。名前の変更だけ。監査なし）。
     *
     * @param reason 理由
     */
    record Invalid(GroupNameValidation.Reason reason) implements GroupChangeResult {

        /** 値が null でないことを確かめる。 */
        public Invalid {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 業務の拒否（使用中の削除を除く。監査に FAILURE）。
     *
     * @param rejection 理由（{@code IN_USE} は {@link InUse} で返す）
     */
    record Rejected(GroupRejection rejection) implements GroupChangeResult {

        /** 値が null でなく、使用中でないことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
            if (rejection == GroupRejection.IN_USE) {
                throw new IllegalArgumentException("使用中の削除は InUse で返します");
            }
        }
    }

    /**
     * メンバーかロールの割り当てが残る削除の拒否（409 {@code GROUP_IN_USE}、応答に残りの数。BR4.1・BR4.2。監査に FAILURE）。
     *
     * @param members 残りのメンバーの数
     * @param assignedRoles 残りのロールの割り当ての数
     */
    record InUse(int members, int assignedRoles) implements GroupChangeResult {

        /**
         * 数が負でないことを確かめる（外部キーの違反の読み替えでは、数え直した時点で 0 になりうるため、0 を許す）。
         */
        public InUse {
            if (members < 0 || assignedRoles < 0) {
                throw new IllegalArgumentException("数は 0 以上です");
            }
        }
    }

    /** 排他の待ちの上限切れ（409 {@code GROUP_BUSY}。監査なし）。 */
    record Busy() implements GroupChangeResult {}
}
