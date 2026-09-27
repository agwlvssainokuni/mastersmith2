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

import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * 件名の取り出しの性質ベースのテスト（jqwik、BR4.2、NFR2.8）。
 *
 * <p>失敗したときの乱数の種は jqwik の報告に出る（{@code @Property(seed = "...")} に与えて再現する）。
 */
class RenderedMailInspectorPropertyTest {

    /** 改行・タブ・全角の空白・文字参照・日本語を含む title の文面（{@code <} は含めない）。 */
    @Provide
    Arbitrary<String> titleTexts() {
        Arbitrary<String> pieces = Arbitraries.oneOf(
                Arbitraries.strings().withCharRange(' ', ';').ofMaxLength(5),
                Arbitraries.strings().withCharRange('=', '~').ofMaxLength(5),
                Arbitraries.strings().withCharRange('ぁ', 'ん').ofMaxLength(5),
                Arbitraries.of(
                        "\r", "\n", "\r\n", "\t", "\u3000", "\u00A0", "\u2028", "&#10;", "&#13;", "&amp;", "&lt;"));
        return pieces.list().ofMaxSize(12).map(list -> String.join("", list));
    }

    @Property(tries = 500)
    @Label("a subject never contains CR or LF, has no surrounding whitespace and no consecutive spaces")
    void subjectIsASingleCleanLine(@ForAll("titleTexts") String text) {
        Optional<String> subject = RenderedMailInspector.subject("<title>" + text + "</title>");

        subject.ifPresent(value -> {
            assertThat(value)
                    .isNotEmpty()
                    .doesNotContain("\r")
                    .doesNotContain("\n")
                    .doesNotContain("  ");
            assertThat(value).isEqualTo(value.strip());
        });
    }
}
