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
 * 入力の誤りの理由（BR1.6 の fieldErrors の {@code reason}）。U1 の読み取りの値は増やさない（NFR4.5）。U2（Intent
 * 261016-business-data-views の B4）が書き込みの理由を足す（entities.md の FieldViolation.reason。画面は理由から文を作る）。
 */
public enum BusinessDataFieldErrorReason {
    /** 使えないカラム（NONE・DSL に無い・検索や並べ替えに使えない・演算子と型の表に無い組。どれも同じ値）。 */
    NOT_ALLOWED,
    /** 型として読めない・長すぎる（1,000 字を超える）・向きが不正。 */
    INVALID_VALUE,
    /** 範囲の下限が上限より大きい。 */
    RANGE_REVERSED,
    /** 選択肢に無い。 */
    NOT_A_CHOICE,
    /** 主キーのカラムの欠け・余り。 */
    KEY_MISMATCH,
    /** 必須のカラムが空・欠け（U2。BR3.3）。 */
    REQUIRED,
    /** 文字数が下限に足りない（U2。BR3.4）。 */
    TOO_SHORT,
    /** 文字数が上限を超える（U2。BR3.4）。 */
    TOO_LONG,
    /** 数の範囲の外（U2。BR3.5）。 */
    OUT_OF_RANGE,
    /** 桁が多すぎる（DECIMAL の桁・秒の端数。U2。BR3.5）。 */
    TOO_MANY_DIGITS,
    /** 正規表現に合わない（U2。BR4.1）。 */
    PATTERN_MISMATCH,
    /** 正規表現の照合が時間の上限を超えた（U2。BR4.2）。 */
    PATTERN_TIMEOUT,
    /** 同じ値の行がある（U2。BR5）。 */
    DUPLICATE,
    /** 参照先に無い値（U2。BR6.3）。 */
    REFERENCE_NOT_FOUND,
    /** NULL を許さないカラムに NULL（U2。BR3.2・BR6.3）。 */
    NOT_NULL,
    /** 入力できないカラムの NOT NULL で保存できない（利用者には直せない。U2。BR6.3）。 */
    NOT_EDITABLE_NOT_NULL,
    /** 変えるカラムが1つも無い更新（U2。BR2.4）。 */
    NO_CHANGE
}
