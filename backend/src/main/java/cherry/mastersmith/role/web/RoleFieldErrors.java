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
package cherry.mastersmith.role.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.role.domain.RoleNameValidation;
import cherry.mastersmith.user.domain.FieldErrorReason;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ロールと権限の管理の入力の誤りの応答（400 {@code VALIDATION_FAILED} と {@code fieldErrors}。group の {@code GroupFieldErrors} と同じ形。
 * BR1.1〜BR1.3・BR2.3・BR4.1・BR4.3・BR4.4・BR4.12）。項目の名前と理由だけを載せ、入れた値を載せない。
 */
final class RoleFieldErrors {

    /** 項目ごとの誤りを載せる追加の項目の名前。 */
    static final String FIELD_ERRORS = "fieldErrors";

    /** 名前の項目。 */
    static final String NAME = "name";

    private RoleFieldErrors() {}

    /**
     * 名前の誤りの業務エラーを作る。理由は既存の入力の誤りの理由に写す（空 → {@code REQUIRED}、長すぎ → {@code TOO_LONG}、使えない
     * 文字 → {@code INVALID_CHARACTER}）。
     *
     * @param reason 名前の誤りの理由
     * @return 業務エラー
     */
    static BusinessException name(RoleNameValidation.Reason reason) {
        FieldErrorReason fieldReason =
                switch (reason) {
                    case INVALID_BLANK -> FieldErrorReason.REQUIRED;
                    case INVALID_TOO_LONG -> FieldErrorReason.TOO_LONG;
                    case INVALID_CONTROL_CHARACTER -> FieldErrorReason.INVALID_CHARACTER;
                };
        return of(NAME, fieldReason);
    }

    /**
     * 項目の誤りの業務エラーを作る。
     *
     * @param field 項目の名前
     * @param reason 理由
     * @return 業務エラー
     */
    static BusinessException of(String field, FieldErrorReason reason) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("reason", reason.name());
        return new BusinessException(CommonProblemTypes.VALIDATION_FAILED, null, Map.of(FIELD_ERRORS, List.of(item)));
    }
}
