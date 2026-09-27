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
package cherry.mastersmith.mail.template;

import java.util.List;

/**
 * 本番のテンプレートの一覧（BR2.2）。テンプレートを足す単位が、ここに行を足し、置き場
 * {@code backend/src/main/resources/mail/templates/} に ja・en のファイル（{@code <templateId>_<language>.html}）を置く。
 *
 * <p>U1 の Bolt（B1）では空。招待（{@code invitation}、差し込みは {@code registrationUrl}・{@code validityHours}）は U3 の Bolt（B3）
 * で足す（契約 C10）。
 */
public final class MailTemplateCatalog {

    /** 本番のテンプレートの置き場（クラスパスの上）。 */
    public static final String LOCATION = "classpath:mail/templates/";

    /** 本番のテンプレートの一覧（B1 では空）。 */
    public static final List<MailTemplateDefinition> DEFINITIONS = List.of();

    private MailTemplateCatalog() {}
}
