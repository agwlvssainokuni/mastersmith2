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
 * 読み取りの API の想定内の拒否（判定の順 BR1.1 の ②〜⑧）。業務処理の層が結果の型で返し、画面入出力の層が {@code switch} で場合を
 * 尽くして問題の種類に変える。
 */
public enum ReadRejection {
    /** ② DSL が適用されていない（404 {@code BUSINESS_DSL_NOT_APPLIED}）。 */
    DSL_NOT_APPLIED,
    /** ③ DSL に無いスキーマかテーブル（404 {@code BUSINESS_TABLE_NOT_FOUND}）。 */
    TABLE_NOT_FOUND,
    /** ④ テーブルの主権限が NONE（403 の共通の {@code ACCESS_DENIED}）。 */
    ACCESS_DENIED,
    /** ⑤ 主キーとビューの決まりで詳細が許されない（409 {@code BUSINESS_ROW_OPERATION_NOT_ALLOWED}）。 */
    ROW_OPERATION_NOT_ALLOWED,
    /** ⑥ 入力の誤り・DB の値の型の誤り（400 の共通の {@code VALIDATION_FAILED}）。 */
    VALIDATION_FAILED,
    /** ⑦ 行が無い（404 {@code BUSINESS_ROW_NOT_FOUND}）。 */
    ROW_NOT_FOUND,
    /** ⑧-1 接続できない・設定が無い（503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}）。 */
    TARGET_DB_UNAVAILABLE,
    /** ⑧-2 時間切れ（503 {@code BUSINESS_TARGET_DB_TIMEOUT}）。 */
    TARGET_DB_TIMEOUT,
    /** ⑧-3 定義との食い違い・保存済みの値を DSL の型で読めない（409 {@code BUSINESS_TARGET_DB_MISMATCH}）。 */
    TARGET_DB_MISMATCH,
    /** ⑧-4 詳細で2行以上が当たった（409 {@code BUSINESS_ROW_NOT_UNIQUE}）。 */
    ROW_NOT_UNIQUE
}
