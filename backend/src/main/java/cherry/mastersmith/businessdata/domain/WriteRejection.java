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
 * 書き込みの API の想定内の拒否（Intent 261016-business-data-views の U2、判定の順 BR1.1 の ②〜⑩）。業務処理の層が結果の型で返し、
 * 画面入出力の層が {@code switch} で場合を尽くして問題の種類に変える。監査の理由の値への写しは
 * {@link BusinessDataAuditReason#of(WriteRejection)} の1か所で決める。
 */
public enum WriteRejection {
    /** ② DSL が適用されていない（404 {@code BUSINESS_DSL_NOT_APPLIED}）。 */
    DSL_NOT_APPLIED,
    /** ③ DSL に無いスキーマかテーブル（404 {@code BUSINESS_TABLE_NOT_FOUND}）。 */
    TABLE_NOT_FOUND,
    /** ④・⑦ テーブルが NONE・補助権限が無い・FULL のカラムが無い（403 の共通の {@code ACCESS_DENIED}）。 */
    ACCESS_DENIED,
    /** ⑤ 画面の定義が古い（409 {@code BUSINESS_DEFINITION_CHANGED}）。 */
    DEFINITION_CHANGED,
    /** ⑥ 主キーとビューの決まり（409 {@code BUSINESS_ROW_OPERATION_NOT_ALLOWED}）。 */
    ROW_OPERATION_NOT_ALLOWED,
    /** ⑧ 書き込みの設定が無い（409 {@code BUSINESS_WRITE_NOT_CONFIGURED}）。 */
    WRITE_NOT_CONFIGURED,
    /** ⑨ 入力の誤り（400 の共通の {@code VALIDATION_FAILED}）。 */
    VALIDATION_FAILED,
    /** ⑩-1 接続を借りられない・保存されていない対象DB の失敗（503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}）。 */
    TARGET_DB_UNAVAILABLE,
    /** ⑩-2 表・カラムが対象DB に無い（409 {@code BUSINESS_TARGET_DB_MISMATCH}）。 */
    TARGET_DB_MISMATCH,
    /** ⑩-3 同じ値がある（409 {@code BUSINESS_DUPLICATE_VALUE}）。 */
    DUPLICATE_VALUE,
    /** ⑩-4 参照先に無い値（409 {@code BUSINESS_REFERENCE_NOT_FOUND}）。 */
    REFERENCE_NOT_FOUND,
    /** ⑩-5 DB が拒んだ保存・違反の確かめの失敗（409 {@code BUSINESS_SAVE_REJECTED}）。 */
    SAVE_REJECTED,
    /** ⑩-6 当たった行が 0（404 {@code BUSINESS_ROW_NOT_FOUND}）。 */
    ROW_NOT_FOUND,
    /** ⑩-7 当たった行が 2 以上（409 {@code BUSINESS_ROW_NOT_UNIQUE}）。 */
    ROW_NOT_UNIQUE,
    /** ⑩-8 確定の段の時間切れ・断絶（503 {@code BUSINESS_TARGET_DB_TIMEOUT}。確定したか分からない）。 */
    TARGET_DB_TIMEOUT
}
