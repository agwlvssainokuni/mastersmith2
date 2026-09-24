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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import java.util.Objects;

/**
 * DSL の操作の要求の文脈（操作した管理者、送り手、文言の表示言語）。画面入出力の層が要求から作る。
 *
 * @param actorUserId 操作した管理者の利用者 ID
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（無ければ null）
 * @param traceId トレースID（無ければ null）
 * @param language 文言の表示言語
 */
public record DslRequestContext(
        long actorUserId, String sourceIp, String userAgent, String traceId, DisplayLanguage language) {

    /** 必須の値を確かめる。 */
    public DslRequestContext {
        Objects.requireNonNull(sourceIp, "sourceIp は必須です");
        Objects.requireNonNull(language, "language は必須です");
    }
}
