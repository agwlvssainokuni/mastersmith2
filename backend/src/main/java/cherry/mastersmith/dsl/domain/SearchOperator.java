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

/** 検索の演算子（DSL の {@code search.operator}・{@code options.lookupSearch[].operator}。DSL には名前のまま書く）。 */
public enum SearchOperator {
    /** 完全一致。 */
    EQUALS,
    /** 部分一致。 */
    CONTAINS,
    /** 範囲。 */
    RANGE,
    /** 選択肢。 */
    CHOICE,
    /** いずれかに一致。 */
    IN
}
