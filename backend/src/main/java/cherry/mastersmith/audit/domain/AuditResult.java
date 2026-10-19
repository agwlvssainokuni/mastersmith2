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
package cherry.mastersmith.audit.domain;

/** 監査イベントの結果（BR1.2）。出来事の種類から決まる。 */
public enum AuditResult {
    /** 成功（ログインの成功・ログアウト）。 */
    SUCCESS,
    /** 失敗（ログインの失敗・アクセスの拒否）。 */
    FAILURE,
    /**
     * 確定したか分からない（業務データの書き込みの確定の段の時間切れで、確定したか分からない。Intent 261016-business-data-views の U2、
     * NFR 設計 Q5: A）。列 {@code result} は VARCHAR(16) で CHECK の制約が無いため、値を足すのに移行は要らない。
     */
    UNKNOWN
}
