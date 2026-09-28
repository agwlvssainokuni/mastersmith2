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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 招待メールのテンプレートの中身の単体テスト（U3 の BR10.1〜BR10.3、契約 C10、AC3.1.2・AC3.1.3・AC3.1.8・AC3.1.9）。本番の一覧と
 * 置き場で描く。
 */
class InvitationTemplateContentTest {

    private static final String URL = "https://example.com/app/register#token=" + "A".repeat(43);

    private static final MailTemplateRegistry REGISTRY = MailTemplateRegistry.prepare(
            MailTemplateCatalog.DEFINITIONS, MailTemplateCatalog.LOCATION, new PathMatchingResourcePatternResolver());

    private static String render(String language, String hours) {
        return REGISTRY.render("invitation", language, Map.of("registrationUrl", URL, "validityHours", hours))
                .orElseThrow();
    }

    @Test
    @DisplayName("the ja mail states the validity period from the variable, the invitation and the no-action note")
    void japaneseBody() {
        String html = render("ja", "24");

        assertThat(html)
                .contains("<html lang=\"ja\">")
                .contains("<title>MasterSmith への招待</title>")
                .contains("このリンクは 24 時間有効です")
                .contains("招待されました")
                .contains("ログインできます")
                .contains("何もしなくてかまいません");
    }

    @Test
    @DisplayName("the en mail states the same content in English")
    void englishBody() {
        String html = render("en", "24");

        assertThat(html)
                .contains("<html lang=\"en\">")
                .contains("<title>Invitation to MasterSmith</title>")
                .contains("This link is valid for 24 hours.")
                .contains("invited")
                .contains("log in")
                .contains("do not need to do anything")
                .doesNotContain("有効");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ja", "en"})
    @DisplayName("with 48 hours the body shows 48 and never 24 (the number comes only from the variable)")
    void validityFollowsTheVariable(String language) {
        String html = render(language, "48");

        assertThat(html).contains("48").doesNotContain("24");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ja", "en"})
    @DisplayName(
            "the link has a descriptive text, the URL also appears as text, and the escaped fragment keeps #token=")
    void linkAndUrlText(String language) {
        String html = render(language, "24");

        assertThat(html).contains("href=\"" + URL + "\"");
        assertThat(html.split(java.util.regex.Pattern.quote(URL), -1)).hasSize(3);
        assertThat(html).containsAnyOf(">登録を完了する</a>", ">Complete your registration</a>");
        assertThat(html).doesNotContain(">こちら</a>").doesNotContain(">here</a>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ja", "en"})
    @DisplayName("no expiry date, no inviter name and no license header appear in the body")
    void noDateNameOrHeader(String language) {
        String html = render(language, "24");

        assertThat(html)
                .doesNotContain("2026")
                .doesNotContain("Licensed")
                .doesNotContain("{{")
                .doesNotContainPattern("\\d{4}-\\d{2}-\\d{2}");
        assertThat(RenderedMailInspector.inspect("invitation", language, html)
                        .orElseThrow()
                        .subject())
                .isNotBlank();
    }
}
