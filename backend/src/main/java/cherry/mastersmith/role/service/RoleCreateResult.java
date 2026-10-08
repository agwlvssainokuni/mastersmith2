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
import java.util.Objects;

/** ロールの作成の結果（FS の 2.1）。web の層が {@code switch} で場合を尽くして応答に変える（BR13.1）。文字列は種類だけ（D-5）。 */
public sealed interface RoleCreateResult
        permits RoleCreateResult.Created,
                RoleCreateResult.InvalidName,
                RoleCreateResult.Rejected,
                RoleCreateResult.Busy {

    /**
     * 作った（201）。
     *
     * @param role 作ったロール
     */
    record Created(RoleDetail role) implements RoleCreateResult {

        /** 値が null でないことを確かめる。 */
        public Created {
            Objects.requireNonNull(role, "role");
        }

        @Override
        public String toString() {
            return "Created[" + role + "]";
        }
    }

    /**
     * 名前の入力の誤り（400。監査なし）。
     *
     * @param reason 理由
     */
    record InvalidName(RoleNameValidation.Reason reason) implements RoleCreateResult {

        /** 値が null でないことを確かめる。 */
        public InvalidName {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 業務の拒否（名前の重なり。監査に FAILURE）。
     *
     * @param rejection 理由
     */
    record Rejected(RoleRejection rejection) implements RoleCreateResult {

        /** 値が null でないことを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** 名前の鍵の待ちの上限切れ（409 {@code ROLE_BUSY}。監査なし）。 */
    record Busy() implements RoleCreateResult {}
}
