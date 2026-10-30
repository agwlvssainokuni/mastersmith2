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

import java.util.List;
import java.util.Objects;

/** 書き込みの要求の確かめの結果（Intent 261016-business-data-views の U2、BR2・BR3・BR4）。 */
public sealed interface WriteValidation {

    /**
     * 確かめを通った。
     *
     * @param values 型つきの値（送った順。作成は送ったカラムだけ、更新は変えたカラムだけ、削除は空）
     * @param key 型として読んだ主キー（更新・削除。作成では null）
     * @param held 保留した誤り（作成の必須の参照項目の空の誤り。Intent 261029-reference-lookup-cleanup の U2、BR3.6。保留が無ければ
     *     空）
     */
    record Valid(List<TypedValue> values, RowKey key, List<BusinessDataFieldError> held) implements WriteValidation {

        /** 一覧を変更できないものにする。 */
        public Valid {
            values = List.copyOf(values);
            held = held == null ? List.of() : List.copyOf(held);
        }

        /**
         * 保留した誤りの無い結果を作る。
         *
         * @param values 型つきの値
         * @param key 型として読んだ主キー
         */
        public Valid(List<TypedValue> values, RowKey key) {
            this(values, key, List.of());
        }

        /** カラムの数だけを出す（保留した誤りがあるときだけ、その数を足す）。 */
        @Override
        public String toString() {
            return "Valid[values=" + values.size() + ", key=" + key + (held.isEmpty() ? "" : ", held=" + held.size())
                    + "]";
        }
    }

    /**
     * 誤りがある（400 {@code VALIDATION_FAILED}）。
     *
     * @param errors カラムに関わる誤り（カラムごとに最初の1つ）
     */
    record Invalid(List<BusinessDataFieldError> errors) implements WriteValidation {

        /** 誤りが1つ以上あり、一覧を変更できないものにする。 */
        public Invalid {
            Objects.requireNonNull(errors, "errors は必須です");
            errors = List.copyOf(errors);
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("誤りは1つ以上です");
            }
        }
    }
}
