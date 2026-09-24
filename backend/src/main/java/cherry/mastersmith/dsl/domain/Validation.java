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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * バリデーション1つ（値）。値は種類によって数（{@code minLength}・{@code maxLength}・{@code min}・{@code max}）か文字
 * （{@code pattern}）のどちらかで持つ。{@code required}・{@code unique} は値を持たない。
 *
 * <p>{@code pattern} は正しい正規表現かを確かめただけで、U2 は値に当てはめない。当てはめる後続の Intent は、重い正規表現で
 * 止まらないよう時間の上限つきで当てはめる（U2 の NFR 要件の 4節）。
 *
 * @param type 種類
 * @param number 数の値（{@code minLength}・{@code maxLength}・{@code min}・{@code max} のとき。ほかは null）
 * @param text 文字の値（{@code pattern} のとき。ほかは null）
 * @param origin 出どころ
 * @param message 誤りの文言（無ければ null）
 */
public record Validation(
        ValidationType type, BigDecimal number, String text, ValidationOrigin origin, DisplayName message) {

    /** 種類と出どころが必須で、種類に合う値を持つことを確かめる。 */
    public Validation {
        Objects.requireNonNull(type, "validation.type は必須です");
        Objects.requireNonNull(origin, "validation.origin は必須です");
        boolean textual = type == ValidationType.PATTERN;
        boolean numeric = type.hasValue() && !textual;
        if (numeric != (number != null) || textual != (text != null)) {
            throw new IllegalArgumentException("validation の値が種類と合いません");
        }
    }
}
