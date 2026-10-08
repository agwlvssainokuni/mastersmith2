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

import cherry.mastersmith.role.domain.RoleRejection;
import java.util.Objects;

/**
 * 割り当てと外しの結果（FS の 2.8、BR6）。web の層が {@code switch} で場合を尽くして応答に変える（BR13.1）。文字列は種類だけ（計画の D-5）。
 */
public sealed interface RoleAssignmentResult
        permits RoleAssignmentResult.Done,
                RoleAssignmentResult.Rejected,
                RoleAssignmentResult.Busy,
                RoleAssignmentResult.GroupBusy {

    /** 割り当てた・外した（204）。 */
    record Done() implements RoleAssignmentResult {}

    /**
     * 業務の拒否（{@code ROLE_NOT_FOUND}・{@code USER_NOT_FOUND}・{@code GROUP_NOT_FOUND}・{@code ROLE_NO_CHANGE}。監査に FAILURE）。
     *
     * @param rejection 理由
     */
    record Rejected(RoleRejection rejection) implements RoleAssignmentResult {

        /** 値が null でないことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** ロールの行・割り当ての主キーの待ちの上限切れ（409 {@code ROLE_BUSY}。監査なし）。 */
    record Busy() implements RoleAssignmentResult {}

    /** グループの行の待ちの上限切れ（409 group の {@code GROUP_BUSY}。監査なし。BR6.6）。 */
    record GroupBusy() implements RoleAssignmentResult {}
}
