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
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 読み取りと書き込みの API の入力の誤りの応答（400 {@code VALIDATION_FAILED}。BR1.6・BR9.2・BR10.3）と、書き込みのカラムを示す 409（U2）。
 * {@code fieldErrors} にはカラムの名前と理由（と正規表現に合わないときの DSL の文。U2）だけを載せ、入れた値を載せない。カラムに関わる誤りが無い（ページ・件数の誤り、DB の値の型の誤り）ときは {@code fieldErrors} を付けない。
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
            properties.put(FIELD_ERRORS, items(errors));
        }
        properties.put(DEFINITION_CHANGED, definitionChanged);
        return new BusinessException(CommonProblemTypes.VALIDATION_FAILED, null, properties);
    }

    /**
     * カラムを示す業務の失敗（409 の同じ値・参照先に無い値・保存の拒否。U2）の業務エラーを作る。誤りが無ければ {@code fieldErrors} を
     * 付けない。
     *
     * @param type 問題の種類
     * @param errors カラムに関わる誤り（無ければ空）
     * @return 業務エラー
     */
    static BusinessException withFieldErrors(ProblemType type, List<BusinessDataFieldError> errors) {
        if (errors.isEmpty()) {
            return new BusinessException(type);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(FIELD_ERRORS, items(errors));
        return new BusinessException(type, null, properties);
    }

    /** 誤りを、カラムの名前・理由・（正規表現の）DSL の文だけの項目にする（値を入れない。U2 の BR12.3）。 */
    private static List<Map<String, Object>> items(List<BusinessDataFieldError> errors) {
        return errors.stream()
                .map(error -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("field", error.field());
                    item.put("reason", error.reason().name());
                    if (error.message() != null) {
                        Map<String, String> message = new LinkedHashMap<>();
                        message.put("ja", error.message().ja());
                        message.put("en", error.message().en());
                        item.put("message", message);
                    }
                    return item;
                })
                .toList();
    }
}
