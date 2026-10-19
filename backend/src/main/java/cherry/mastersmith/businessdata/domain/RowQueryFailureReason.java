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

/** 行の SQL の失敗の理由（reliability-design.md の 2節、BR8.5・BR8.6、NFR 設計のレビュー R-01・D1: A）。 */
public enum RowQueryFailureReason {
    /** 時間切れ（503 {@code BUSINESS_TARGET_DB_TIMEOUT}）。 */
    TIMEOUT,
    /** 接続の失敗（503 {@code BUSINESS_TARGET_DB_UNAVAILABLE}）。 */
    UNAVAILABLE,
    /** DSL にあるテーブル・カラムが対象DB に無い（409 {@code BUSINESS_TARGET_DB_MISMATCH}）。 */
    MISMATCH,
    /** バインド・実行の段階の値の型の誤り（400 {@code VALIDATION_FAILED}。BR8.6）。 */
    INVALID_VALUE,
    /** 結果を読む段階の型の変換の失敗（409 {@code BUSINESS_TARGET_DB_MISMATCH}。D1: A）。 */
    READ_CONVERSION,
    /** 想定外（500）。 */
    UNEXPECTED
}
