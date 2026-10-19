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

/** 詳細の本文の主キーの確かめの結果（BR6.2）。 */
public sealed interface KeyValidation {

    /**
     * 確かめを通った。
     *
     * @param key 型として読んだ主キー
     */
    record Valid(RowKey key) implements KeyValidation {

        /** 主キーが必須であることを確かめる。 */
        public Valid {
            Objects.requireNonNull(key, "key は必須です");
        }
    }

    /**
     * 誤りがある（400 {@code VALIDATION_FAILED}）。
     *
     * @param errors カラムに関わる誤り
     */
    record Invalid(List<BusinessDataFieldError> errors) implements KeyValidation {

        /** 一覧を変更できないものにする。 */
        public Invalid {
            errors = List.copyOf(errors);
        }
    }
}
