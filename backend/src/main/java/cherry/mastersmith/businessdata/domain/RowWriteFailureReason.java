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
 * 書き込みの SQL の失敗の理由（Intent 261016-business-data-views の U2、reliability-design.md の 2節、計画の D1〜D4）。業務処理の層が
 * 応答の code に写す（写しは {@code BusinessDataWriteService} の1か所）。
 */
public enum RowWriteFailureReason {
    /** 接続できない・送る前の時間切れ・断絶・違反の後の段の時間切れ（503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}。保存されていない）。 */
    UNAVAILABLE,
    /** 競合による巻き戻し（デッドロック・直列化の失敗・ロックの待ち切れ。D1: A の 503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}）。 */
    CONFLICT,
    /** 書き込みの文を送った後・確定の段の時間切れ・断絶（503 {@code BUSINESS_TARGET_DB_TIMEOUT}。確定したか分からない）。 */
    TIMEOUT,
    /** 表・カラムが対象DB に無い（409 {@code BUSINESS_TARGET_DB_MISMATCH}）。 */
    MISMATCH,
    /** 一意の違反（409 {@code BUSINESS_DUPLICATE_VALUE}。確かめでカラムを決める）。 */
    DUPLICATE,
    /** 作成・更新の外部キーの違反（409 {@code BUSINESS_REFERENCE_NOT_FOUND}。確かめでカラムを決める）。 */
    REFERENCE_NOT_FOUND,
    /** NOT NULL の違反（409 {@code BUSINESS_SAVE_REJECTED}）。 */
    NOT_NULL,
    /**
     * DB が拒んだ保存（削除の外部キー・長さ・桁あふれ・CHECK・日時の範囲・文字集合・NUL・更新できないビュー・形の誤り・確定の段の遅延の
     * 制約の違反・違反の後の段の失敗。409 {@code BUSINESS_SAVE_REJECTED}）。
     */
    REJECTED,
    /** 想定外（500）。 */
    UNEXPECTED
}
