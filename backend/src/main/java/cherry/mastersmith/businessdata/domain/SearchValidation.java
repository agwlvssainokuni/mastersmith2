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

/** 一覧と検索の本文の確かめの結果（BR1.6・BR4・BR5.1・BR5.2）。 */
public sealed interface SearchValidation {

    /**
     * 確かめを通った。
     *
     * @param conditions 型として読んだ条件（値が空の条件は含めない）
     * @param page ページの指定
     */
    record Valid(List<SearchCondition> conditions, PageRequest page) implements SearchValidation {

        /** 一覧を変更できないものにする。 */
        public Valid {
            conditions = List.copyOf(conditions);
            Objects.requireNonNull(page, "page は必須です");
        }
    }

    /**
     * 誤りがある（400 {@code VALIDATION_FAILED}）。ページ・件数だけの誤りは fieldErrors を付けない（空）。
     *
     * @param errors カラムに関わる誤り（要求の順）
     */
    record Invalid(List<BusinessDataFieldError> errors) implements SearchValidation {

        /** 一覧を変更できないものにする。 */
        public Invalid {
            errors = List.copyOf(errors);
        }
    }
}
