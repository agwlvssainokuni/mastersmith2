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
package cherry.mastersmith.businessdata.domain;

import java.util.Optional;

/**
 * 業務データの操作の監査の理由の値（Intent 261016-business-data-views の U2、BR11.3・BR11.6、entities.md の
 * BusinessDataChangedEvent.failureReason）。応答の code とは別の列挙で、どれも 32 字以内（監査の列 {@code failure_reason} は
 * VARCHAR(32)）。{@code audit} が {@code AuditFailureReason} に写す（{@code businessdata} は {@code audit} に依存しない）。
 */
public enum BusinessDataAuditReason {
    /** 主キーとビューの決まり（応答の code は {@code BUSINESS_ROW_OPERATION_NOT_ALLOWED}。34 字のため短くした）。 */
    BUSINESS_ROW_OP_NOT_ALLOWED,
    /** 当たった行が 0。 */
    BUSINESS_ROW_NOT_FOUND,
    /** 当たった行が 2 以上。 */
    BUSINESS_ROW_NOT_UNIQUE,
    /** 同じ値がある。 */
    BUSINESS_DUPLICATE_VALUE,
    /** 参照先に無い値。 */
    BUSINESS_REFERENCE_NOT_FOUND,
    /** DB が拒んだ保存・違反の確かめの失敗。 */
    BUSINESS_SAVE_REJECTED,
    /** 接続を借りられない・保存されていない対象DB の失敗。 */
    BUSINESS_TARGET_DB_UNAVAILABLE,
    /** 確定の段の時間切れ・断絶（結果は {@code UNKNOWN}）。 */
    BUSINESS_TARGET_DB_TIMEOUT,
    /** 表・カラムが対象DB に無い。 */
    BUSINESS_TARGET_DB_MISMATCH;

    /**
     * 拒否から監査の理由の値を返す（BR11.3・BR11.6 の写しの1か所。名前が同じことに頼らない）。監査に残さない拒否（401・403・400・
     * DSL とテーブルの 404・定義が古い・設定が無い）は空。
     *
     * @param rejection 拒否
     * @return 監査の理由の値（残さない拒否は空）
     */
    public static Optional<BusinessDataAuditReason> of(WriteRejection rejection) {
        return switch (rejection) {
            case DSL_NOT_APPLIED,
                    TABLE_NOT_FOUND,
                    ACCESS_DENIED,
                    DEFINITION_CHANGED,
                    WRITE_NOT_CONFIGURED,
                    VALIDATION_FAILED -> Optional.empty();
            case ROW_OPERATION_NOT_ALLOWED -> Optional.of(BUSINESS_ROW_OP_NOT_ALLOWED);
            case ROW_NOT_FOUND -> Optional.of(BUSINESS_ROW_NOT_FOUND);
            case ROW_NOT_UNIQUE -> Optional.of(BUSINESS_ROW_NOT_UNIQUE);
            case DUPLICATE_VALUE -> Optional.of(BUSINESS_DUPLICATE_VALUE);
            case REFERENCE_NOT_FOUND -> Optional.of(BUSINESS_REFERENCE_NOT_FOUND);
            case SAVE_REJECTED -> Optional.of(BUSINESS_SAVE_REJECTED);
            case TARGET_DB_UNAVAILABLE -> Optional.of(BUSINESS_TARGET_DB_UNAVAILABLE);
            case TARGET_DB_TIMEOUT -> Optional.of(BUSINESS_TARGET_DB_TIMEOUT);
            case TARGET_DB_MISMATCH -> Optional.of(BUSINESS_TARGET_DB_MISMATCH);
        };
    }
}
