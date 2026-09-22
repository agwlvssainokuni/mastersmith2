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
package cherry.mastersmith.common.error.web;

import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;

/**
 * 問題の種類の説明（JSON の応答、BR5.11）。個々の要求の内容は載せない（BR5.12）。
 *
 * @param code code
 * @param status 状態コード
 * @param title 名前
 * @param description どんなときに起きるか
 * @param resolution 利用者がすべきこと（無ければ null）
 * @param language 表示言語（{@code ja}・{@code en}）
 */
public record ProblemTypeResponse(
        String code, int status, String title, String description, String resolution, String language) {

    /**
     * 問題の種類の定義から説明を作る。
     *
     * @param type 問題の種類
     * @param language 表示言語
     * @return 説明
     */
    public static ProblemTypeResponse of(ProblemType type, DisplayLanguage language) {
        return new ProblemTypeResponse(
                type.code(),
                type.status(),
                type.title().in(language),
                type.description().in(language),
                type.resolution() == null ? null : type.resolution().in(language),
                language.tag());
    }
}
