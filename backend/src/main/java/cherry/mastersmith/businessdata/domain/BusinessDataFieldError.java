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
 * カラムに関わる入力の誤り1つ（BR1.6）。値は持たず、要求に入っていたカラムの名前と理由だけを持つ（BR10.3）。
 *
 * @param field 要求に入っていたカラムの名前
 * @param reason 理由
 */
public record BusinessDataFieldError(String field, BusinessDataFieldErrorReason reason) {

    /** 必須の値を確かめる。 */
    public BusinessDataFieldError {
        Objects.requireNonNull(field, "field は必須です");
        Objects.requireNonNull(reason, "reason は必須です");
    }
}
