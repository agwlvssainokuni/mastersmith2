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

/**
 * 業務データの操作の監査の結果（Intent 261016-business-data-views の U2、BR11.2・BR11.3、NFR 設計 Q5: A）。{@code audit} が
 * {@code AuditResult} に写す。
 */
public enum BusinessDataAuditResult {
    /** 成功。 */
    SUCCESS,
    /** 失敗（保存されていない）。 */
    FAILURE,
    /** 確定の段の時間切れで、確定したか分からない（{@link BusinessDataAuditReason#BUSINESS_TARGET_DB_TIMEOUT} だけ）。 */
    UNKNOWN;

    /**
     * 監査の理由の値から結果を決める（時間切れだけ {@link #UNKNOWN}。計画の D3: A）。
     *
     * @param reason 監査の理由の値
     * @return 結果
     */
    public static BusinessDataAuditResult failureOf(BusinessDataAuditReason reason) {
        return reason == BusinessDataAuditReason.BUSINESS_TARGET_DB_TIMEOUT ? UNKNOWN : FAILURE;
    }
}
