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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemTypeHtmlRendererTest {

    private final ProblemTypeHtmlRenderer renderer = new ProblemTypeHtmlRenderer();

    private static final ProblemType DANGEROUS = new ProblemType(
            "HTML_CHARS",
            400,
            new LocalizedText("<script>alert(1)</script>", "Tom & \"Jerry\""),
            new LocalizedText("'引用' と <b>強調</b>", "a < b > c"),
            new LocalizedText("<img src=x onerror=alert(1)>", "it's fine"));

    @Test
    @DisplayName("HTML special characters in the definition are escaped")
    void escapesSpecialCharacters() {
        String html = renderer.render(DANGEROUS, DisplayLanguage.JA);

        assertThat(html).doesNotContain("<script>").doesNotContain("<b>").doesNotContain("<img");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;").contains("&#39;引用&#39;");

        String english = renderer.render(DANGEROUS, DisplayLanguage.EN);
        assertThat(english)
                .contains("Tom &amp; &quot;Jerry&quot;")
                .contains("a &lt; b &gt; c")
                .contains("it&#39;s fine");
    }

    @Test
    @DisplayName("lang attribute and labels follow the display language")
    void followsLanguage() {
        assertThat(renderer.render(CommonProblemTypes.NOT_FOUND, DisplayLanguage.JA))
                .contains("<html lang=\"ja\">")
                .contains("見つかりません")
                .contains("すべきこと");
        assertThat(renderer.render(CommonProblemTypes.NOT_FOUND, DisplayLanguage.EN))
                .contains("<html lang=\"en\">")
                .contains("Not found")
                .contains("What to do");
    }

    @Test
    @DisplayName("renderer has no way to receive request values")
    void acceptsOnlyDefinitionAndLanguage() {
        Method[] publicMethods = Arrays.stream(ProblemTypeHtmlRenderer.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);

        assertThat(publicMethods).hasSize(1);
        assertThat(publicMethods[0].getParameterTypes()).containsExactly(ProblemType.class, DisplayLanguage.class);
    }

    @Test
    @DisplayName("page contains no inline style or script")
    void noInlineStyleOrScript() {
        String html = renderer.render(CommonProblemTypes.INTERNAL_ERROR, DisplayLanguage.JA);

        assertThat(html).doesNotContain("<script").doesNotContain("<style").doesNotContain("style=");
    }

    @Test
    @DisplayName("definition without a resolution is rendered without the resolution item")
    void withoutResolution() {
        ProblemType noResolution = new ProblemType(
                "NO_RESOLUTION", 409, new LocalizedText("名前", "Name"), new LocalizedText("説明", "Desc"), null);

        String html = renderer.render(noResolution, DisplayLanguage.JA);

        assertThat(html).contains("説明").doesNotContain("すべきこと");
    }
}
