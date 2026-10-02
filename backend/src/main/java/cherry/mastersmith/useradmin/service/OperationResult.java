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
package cherry.mastersmith.useradmin.service;

import cherry.mastersmith.useradmin.domain.RejectionReason;
import java.util.Objects;

/**
 * 5つの管理の操作の結果（Intent 260930-user-admin の U3、BR2.3）。業務処理は想定内の失敗をこの型で返し、画面入出力の層が場合を尽くす
 * {@code switch} で応答に変える。
 */
public sealed interface OperationResult
        permits OperationResult.Done, OperationResult.Rejected, OperationResult.OperatorNotAdmin, OperationResult.Busy {

    /** 状態を書き換えて確定した（204）。 */
    record Done() implements OperationResult {}

    /**
     * 業務の理由で拒否した（404 か 409）。状態は変えず、書き込みなしで確定した（失敗の監査を残す）。
     *
     * @param reason 拒否の理由
     */
    record Rejected(RejectionReason reason) implements OperationResult {

        /** 理由が有ることを確かめる。 */
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 操作した人の確かめ直しで、有効な管理者でなかった（403 ACCESS_DENIED。監査は NOT_ADMIN。BR2.6）。 */
    record OperatorNotAdmin() implements OperationResult {}

    /** 行の排他の待ちの上限切れ（409 USER_ADMIN_BUSY）。巻き戻し、監査を残さない（BR3.5）。 */
    record Busy() implements OperationResult {}
}
