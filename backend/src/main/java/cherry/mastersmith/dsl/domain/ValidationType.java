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

/** バリデーションの種類（DSL の {@code validations[].type}。DSL には小文字で始まる名前で書く）。 */
public enum ValidationType {
    REQUIRED("required"),
    MIN_LENGTH("minLength"),
    MAX_LENGTH("maxLength"),
    MIN("min"),
    MAX("max"),
    PATTERN("pattern"),
    UNIQUE("unique");

    private final String dslName;

    ValidationType(String dslName) {
        this.dslName = dslName;
    }

    /**
     * DSL に書く名前を返す。
     *
     * @return DSL に書く名前
     */
    public String dslName() {
        return dslName;
    }

    /**
     * DSL に書いた名前から引く。
     *
     * @param dslName DSL に書いた名前
     * @return バリデーションの種類
     * @throws IllegalArgumentException 知らない名前のとき
     */
    public static ValidationType fromDslName(String dslName) {
        for (ValidationType type : values()) {
            if (type.dslName.equals(dslName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("知らないバリデーションの種類です");
    }

    /**
     * 値（{@code value}）を持つ種類かを返す。
     *
     * @return {@code required}・{@code unique} 以外なら true
     */
    public boolean hasValue() {
        return this != REQUIRED && this != UNIQUE;
    }
}
