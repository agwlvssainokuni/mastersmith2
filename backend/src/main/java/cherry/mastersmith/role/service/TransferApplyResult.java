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
import cherry.mastersmith.role.transfer.TransferErrors;
import java.util.Objects;

/**
 * 権限の YAML の適用の結果（FS の 2.15、BR9.10〜BR9.13）。{@code toString} は種類と数だけ（計画の D-5）。
 */
public sealed interface TransferApplyResult
        permits TransferApplyResult.Applied,
                TransferApplyResult.Invalid,
                TransferApplyResult.TooLarge,
                TransferApplyResult.Rejected,
                TransferApplyResult.Busy {

    /**
     * まとめて確定した（{@code ROLE_TRANSFER_APPLIED} の成功）。
     *
     * @param created 作ったロールの数
     * @param replaced 置き換えたロールの数
     */
    record Applied(int created, int replaced) implements TransferApplyResult {}

    /**
     * 読み込みの拒否か中身の誤り（400 {@code ROLE_TRANSFER_INVALID}、監査に残さない）。
     *
     * @param errors 誤りの一覧
     */
    record Invalid(TransferErrors errors) implements TransferApplyResult {

        /** 一覧が必須であることを確かめる。 */
        public Invalid {
            Objects.requireNonNull(errors, "errors");
        }
    }

    /** 本文の大きさが上限を超えた（413 {@code PAYLOAD_TOO_LARGE}、監査に残さない）。 */
    record TooLarge() implements TransferApplyResult {}

    /**
     * 業務の拒否（{@code ROLE_TRANSFER_STALE}・{@code DSL_NOT_APPLIED}・{@code ROLE_NO_CHANGE}・{@code ROLE_NAME_DUPLICATE}。
     * 監査に FAILURE）。
     *
     * @param rejection 理由
     */
    record Rejected(RoleRejection rejection) implements TransferApplyResult {

        /** 理由が必須であることを確かめる。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }

    /** ほかの確かめ・適用か、ロールの行・名前の鍵の待ちと重なった（409 {@code ROLE_BUSY}、監査に残さない）。 */
    record Busy() implements TransferApplyResult {}
}
