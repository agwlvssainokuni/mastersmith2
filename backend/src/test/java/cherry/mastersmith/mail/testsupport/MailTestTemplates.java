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
package cherry.mastersmith.mail.testsupport;

import cherry.mastersmith.mail.template.MailTemplateDefinition;
import cherry.mastersmith.mail.template.MailTemplateRegistry;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * U1 のテスト用のテンプレートの一覧と置き場（{@code backend/src/test/resources/mail/test-templates/}）。本番の一覧は B1 では空の
 * ため、描画・送信のテストはこの一覧で行う。
 */
public final class MailTestTemplates {

    /** テスト用のテンプレートの置き場。 */
    public static final String LOCATION = "classpath:mail/test-templates/";

    /** 本文の文面・二重引用符で囲んだ属性の値・title の3か所に差し込むテンプレート。 */
    public static final String SAMPLE = "sample";

    /** title の文面に改行と空白を含むテンプレート。 */
    public static final String MULTILINE = "multiline";

    /** テスト用のテンプレートの一覧。 */
    public static final List<MailTemplateDefinition> CATALOG = List.of(
            new MailTemplateDefinition(SAMPLE, Set.of("name", "link")),
            new MailTemplateDefinition(MULTILINE, Set.of("name")));

    private MailTestTemplates() {}

    /**
     * テスト用の一覧と置き場で準備したテンプレートを返す。
     *
     * @return 準備したテンプレート
     */
    public static MailTemplateRegistry registry() {
        return MailTemplateRegistry.prepare(CATALOG, LOCATION, new PathMatchingResourcePatternResolver());
    }
}
