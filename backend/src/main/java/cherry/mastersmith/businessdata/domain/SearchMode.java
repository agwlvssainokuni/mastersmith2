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
 * 読み替え（BR4.6）と演算子と型の表（BR4.9）を当てた後の検索の演算子（契約 C2 の {@code SearchField.operator}）。DSL の
 * {@code IN} は {@link #CHOICE} として扱う。
 */
public enum SearchMode {
    /** 完全一致。 */
    EQUALS,
    /** 部分一致（文字の型だけ）。 */
    CONTAINS,
    /** 範囲（両端を含む。数・日付・日時・時刻だけ）。 */
    RANGE,
    /** 選択肢（固定の選択肢か真偽値だけ）。 */
    CHOICE
}
