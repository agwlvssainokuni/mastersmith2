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
package cherry.mastersmith.dsl.domain;

/** 選択肢の出どころの種類（DSL の {@code options.source}）。 */
public enum OptionSourceKind {
    /** 固定の値と表示名の並び。 */
    FIXED,
    /** 参照先のテーブルの値と表示のカラム。 */
    REFERENCE,
    /** 参照ピッカー（参照先のテーブルを専用の検索条件と一覧で選ぶ）。 */
    LOOKUP
}
