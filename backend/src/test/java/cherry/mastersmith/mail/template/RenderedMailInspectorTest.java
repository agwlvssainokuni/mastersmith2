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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 件名と言語の取り出しの単体テスト（BR4.2・BR4.3、NFR2.8）。 */
class RenderedMailInspectorTest {

    private static String html(String lang, String title) {
        return "<!DOCTYPE html>\n<html" + lang + ">\n<head>" + title + "</head><body><p>本文</p></body></html>";
    }

    @Test
    @DisplayName("the subject is the title text with character references restored and whitespace collapsed")
    void subjectFromTitle() {
        RenderedMail mail = RenderedMailInspector.inspect(
                        "sample", "ja", html(" lang=\"ja\"", "<title>\n  Tom &amp; Jerry\t&lt;招待&gt;\n  です </title>"))
                .orElseThrow();

        assertThat(mail.subject()).isEqualTo("Tom & Jerry <招待> です");
        assertThat(mail.language()).isEqualTo("ja");
        assertThat(mail.templateId()).isEqualTo("sample");
    }

    @Test
    @DisplayName("a character reference for a line break never produces a line break in the subject")
    void encodedLineBreakIsCollapsed() {
        assertThat(RenderedMailInspector.subject("<title>a&#13;&#10;Bcc: x@example.com</title>"))
                .contains("a Bcc: x@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<p>no title</p>", "<title></title>", "<title>  \n\t\u3000 </title>"})
    @DisplayName("a missing, empty or whitespace-only title gives no result")
    void missingOrEmptyTitle(String title) {
        assertThat(RenderedMailInspector.inspect("sample", "ja", html(" lang=\"ja\"", title)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " lang=\"en\"", " lang=\"\"", " xml:lang=\"ja\""})
    @DisplayName("a missing or different lang attribute gives no result")
    void missingOrDifferentLang(String lang) {
        assertThat(RenderedMailInspector.inspect("sample", "ja", html(lang, "<title>件名</title>")))
                .isEmpty();
    }

    @Test
    @DisplayName("lang in single quotes or without quotes is read, and a missing html element gives no lang")
    void langForms() {
        assertThat(RenderedMailInspector.lang("<html class=\"x\" lang='en'>")).contains("en");
        assertThat(RenderedMailInspector.lang("<HTML LANG=ja>")).contains("ja");
        assertThat(RenderedMailInspector.lang("<body lang=\"ja\">")).isEmpty();
    }

    @Test
    @DisplayName("RenderedMail prints neither the subject nor the body")
    void renderedMailToStringHidesContent() {
        RenderedMail mail = new RenderedMail("sample", "en", "secret subject", "<p>secret body</p>");

        assertThat(mail.toString())
                .isEqualTo("RenderedMail[templateId=sample, language=en]")
                .doesNotContain("secret");
    }
}
