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

import java.util.Objects;

/**
 * ピッカーの検索の条件1つ（Intent 261029-reference-lookup-cleanup の U1、entities.md の ReferenceCondition）。本文の形を確かめた後の
 * 要求の値で、カラムはまだ DSL と照らしていない（照らすのは {@code ReferenceLookup}。BR5.3）。値が空の条件は SQL に使わない（BR5.4）。
 *
 * <p>文字列にするときはカラムの名前と、値が空かどうかだけを出す（BR10.1）。
 *
 * @param column 条件のカラムの名前（照らす前）
 * @param value 値（空の文字を含む。1,000 文字まで。BR1.4）
 */
public record ReferenceCondition(String column, String value) {

    /** カラムと値が必須であることを確かめる。 */
    public ReferenceCondition {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(value, "value は必須です");
    }

    /**
     * 値が空か（空の条件は SQL に使わない。BR5.4）。
     *
     * @return 空なら true
     */
    public boolean isEmpty() {
        return value.isEmpty();
    }

    /** カラムの名前と、値が空かどうかだけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "ReferenceCondition[column=" + column + ", value=" + (value.isEmpty() ? "empty" : "***") + "]";
    }
}
