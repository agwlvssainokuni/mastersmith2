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

import cherry.mastersmith.dsl.domain.DisplayName;
import java.util.Objects;

/**
 * カラムに関わる入力の誤り1つ（BR1.6、U2 の entities.md の FieldViolation）。値は持たず、要求に入っていたカラムの名前と理由と、
 * 正規表現に合わないときの DSL の文だけを持つ（BR10.3・U2 の BR12.3）。フォーム全体の誤りでは {@code field} を空の文字にする。
 *
 * @param field 要求に入っていたカラムの名前（フォーム全体の誤りでは空の文字）
 * @param reason 理由
 * @param message 正規表現に合わないときの DSL の文（{@link BusinessDataFieldErrorReason#PATTERN_MISMATCH} で DSL に文があるときだけ。
 *     ほかは null）
 */
public record BusinessDataFieldError(String field, BusinessDataFieldErrorReason reason, DisplayName message) {

    /** 必須の値を確かめる。 */
    public BusinessDataFieldError {
        Objects.requireNonNull(field, "field は必須です");
        Objects.requireNonNull(reason, "reason は必須です");
    }

    /**
     * 文を持たない誤りを作る。
     *
     * @param field 要求に入っていたカラムの名前
     * @param reason 理由
     */
    public BusinessDataFieldError(String field, BusinessDataFieldErrorReason reason) {
        this(field, reason, null);
    }
}
