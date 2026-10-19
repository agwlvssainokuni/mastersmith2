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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 読み取りの API の入力の誤りの応答（400 {@code VALIDATION_FAILED}。BR1.6・BR9.2・BR10.3）。{@code fieldErrors} にはカラムの名前と理由
 * だけを載せ、入れた値を載せない。カラムに関わる誤りが無い（ページ・件数の誤り、DB の値の型の誤り）ときは {@code fieldErrors} を付けない。
 * {@code definitionChanged} は真偽値で必ず付ける。
 */
final class BusinessDataFieldErrors {

    /** 項目ごとの誤りを載せる追加の項目の名前。 */
    static final String FIELD_ERRORS = "fieldErrors";

    /** 定義の識別が変わったかを載せる追加の項目の名前。 */
    static final String DEFINITION_CHANGED = "definitionChanged";

    private BusinessDataFieldErrors() {}

    /**
     * 入力の誤りの業務エラーを作る。
     *
     * @param errors カラムに関わる誤り（無ければ空）
     * @param definitionChanged 要求の識別が今と違うか
     * @return 業務エラー
     */
    static BusinessException of(List<BusinessDataFieldError> errors, boolean definitionChanged) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (!errors.isEmpty()) {
            properties.put(
                    FIELD_ERRORS,
                    errors.stream()
                            .map(error -> {
                                Map<String, String> item = new LinkedHashMap<>();
                                item.put("field", error.field());
                                item.put("reason", error.reason().name());
                                return item;
                            })
                            .toList());
        }
        properties.put(DEFINITION_CHANGED, definitionChanged);
        return new BusinessException(CommonProblemTypes.VALIDATION_FAILED, null, properties);
    }
}
