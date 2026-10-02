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
package cherry.mastersmith.useradmin.domain;

import java.util.Objects;

/**
 * 管理の操作の監査の失敗の理由（BR6.1）。業務の拒否の理由5つと、操作した人の確かめ直しで外れていたこと（{@link #NOT_ADMIN}）。
 * {@code audit} が既存の監査の理由の列挙に写す。
 */
public enum UserAdminAuditFailure {
    /** 対象の利用者がいない。 */
    USER_NOT_FOUND,
    /** 自分自身への操作。 */
    SELF_OPERATION,
    /** 対象が停止中。 */
    TARGET_SUSPENDED,
    /** 変えるものが無い。 */
    NO_CHANGE,
    /** 最後の有効な管理者。 */
    LAST_ACTIVE_ADMIN,
    /** 操作した人の確かめ直しで、有効な管理者でなかった（BR2.6）。 */
    NOT_ADMIN;

    /**
     * 業務の拒否の理由を監査の理由に写す。
     *
     * @param reason 業務の拒否の理由
     * @return 監査の理由
     */
    public static UserAdminAuditFailure of(RejectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        return switch (reason) {
            case USER_NOT_FOUND -> USER_NOT_FOUND;
            case SELF_OPERATION -> SELF_OPERATION;
            case TARGET_SUSPENDED -> TARGET_SUSPENDED;
            case NO_CHANGE -> NO_CHANGE;
            case LAST_ACTIVE_ADMIN -> LAST_ACTIVE_ADMIN;
        };
    }
}
