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

/** グループの作成の結果（FS の 2.1）。web の層が {@code switch} で場合を尽くして応答に変える（BR10.1）。 */
public sealed interface GroupCreateResult
        permits GroupCreateResult.Created,
                GroupCreateResult.Invalid,
                GroupCreateResult.Rejected,
                GroupCreateResult.Busy {

    /**
     * 作った（201）。
     *
     * @param group 作ったグループ
     */
    record Created(GroupView group) implements GroupCreateResult {

        /** 値が null でないことを確かめる。 */
        public Created {
            Objects.requireNonNull(group, "group");
        }
    }

    /**
     * 名前の入力の誤り（400。監査なし）。
     *
     * @param reason 理由
     */
    record Invalid(GroupNameValidation.Reason reason) implements GroupCreateResult {

        /** 値が null でないことを確かめる。 */
        public Invalid {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 業務の拒否（名前の重なり。監査に FAILURE）。
     *
     * @param rejection 理由
     */
    record Rejected(GroupRejection rejection) implements GroupCreateResult {

        /** 値が null でないことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** 名前の鍵の待ちの上限切れ（409 {@code GROUP_BUSY}。監査なし）。 */
    record Busy() implements GroupCreateResult {}
}
