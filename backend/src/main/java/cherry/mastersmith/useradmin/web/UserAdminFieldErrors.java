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
package cherry.mastersmith.useradmin.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.user.domain.FieldError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入力の誤りを、項目ごとの誤り（{@code fieldErrors}）を載せた業務エラーにする（Intent 260930-user-admin の U3、BR1.4・BR5.1）。
 * 招待の {@code invitation.web.FieldErrors} と同じ形で、機能の間で使い回さないためここに置く。
 */
final class UserAdminFieldErrors {

    /** 項目ごとの誤りを載せる追加の項目の名前。 */
    static final String FIELD_ERRORS = "fieldErrors";

    private UserAdminFieldErrors() {}

    /**
     * 入力の誤りの業務エラーを作る（項目の名前と理由だけを載せ、入れた値を載せない）。
     *
     * @param errors 項目ごとの誤り
     * @return 業務エラー（400 {@code VALIDATION_FAILED}）
     */
    static BusinessException validationFailed(List<FieldError> errors) {
        List<Map<String, String>> fieldErrors = errors.stream()
                .map(error -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("field", error.field());
                    item.put("reason", error.reason().name());
                    return item;
                })
                .toList();
        return new BusinessException(CommonProblemTypes.VALIDATION_FAILED, null, Map.of(FIELD_ERRORS, fieldErrors));
    }
}
