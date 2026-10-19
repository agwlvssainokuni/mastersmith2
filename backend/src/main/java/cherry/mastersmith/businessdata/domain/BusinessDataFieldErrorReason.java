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

/** 入力の誤りの理由（BR1.6 の fieldErrors の {@code reason}）。値は増やさない（NFR4.5）。 */
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
    KEY_MISMATCH
}
