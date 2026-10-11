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

import cherry.mastersmith.role.transfer.TransferErrors;
import cherry.mastersmith.role.transfer.TransferPlan;
import java.util.Objects;

/**
 * 権限の YAML の確かめの結果（FS の 2.14、BR9.3〜BR9.9・BR9.13・BR9.15）。どれも監査に残さない。{@code toString} は種類と件数だけ
 * （計画の D-5）。
 */
public sealed interface TransferCheckResult
        permits TransferCheckResult.Checked,
                TransferCheckResult.Invalid,
                TransferCheckResult.TooLarge,
                TransferCheckResult.DslNotApplied,
                TransferCheckResult.Busy {

    /**
     * 変わる点の一覧と指紋を作った（保存しない）。
     *
     * @param plan 変わる点の一覧
     */
    record Checked(TransferPlan plan) implements TransferCheckResult {

        /** 一覧が必須であることを確かめる。 */
        public Checked {
            Objects.requireNonNull(plan, "plan");
        }
    }

    /**
     * 読み込みの拒否か中身の誤り（400 {@code ROLE_TRANSFER_INVALID}）。
     *
     * @param errors 誤りの一覧
     */
    record Invalid(TransferErrors errors) implements TransferCheckResult {

        /** 一覧が必須であることを確かめる。 */
        public Invalid {
            Objects.requireNonNull(errors, "errors");
        }
    }

    /** 本文の大きさが上限を超えた（413 {@code PAYLOAD_TOO_LARGE}）。 */
    record TooLarge() implements TransferCheckResult {}

    /** 適用済みの DSL が無い（409 {@code DSL_NOT_APPLIED}）。 */
    record DslNotApplied() implements TransferCheckResult {}

    /** ほかの確かめ・適用が動いている（409 {@code ROLE_BUSY}）。 */
    record Busy() implements TransferCheckResult {}
}
