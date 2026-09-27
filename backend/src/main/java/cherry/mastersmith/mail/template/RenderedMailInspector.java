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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * 描いた本文から件名と言語を決まった形で取り出す（BR4.2・BR4.3）。Bean ではない純粋な関数。
 *
 * <p>テンプレートは WAR に入る信頼できる入力のため、HTML の解析の部品は使わず、最初の {@code title} 要素と {@code html} 要素の
 * {@code lang} 属性だけを正規表現で取り出す。
 */
public final class RenderedMailInspector {

    /** 最初の title 要素の文面。 */
    private static final Pattern TITLE =
            Pattern.compile("<title\\b[^>]*>(.*?)</title\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 最初の html 要素の開始タグ。 */
    private static final Pattern HTML_TAG = Pattern.compile("<html\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    /** 開始タグの中の lang 属性（二重引用符・単一引用符・引用符なし）。 */
    private static final Pattern LANG =
            Pattern.compile("\\slang\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))", Pattern.CASE_INSENSITIVE);

    /** 1つにまとめる空白（Unicode の空白。タブ・改行・全角の空白を含む）。 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private RenderedMailInspector() {}

    /**
     * 描いた本文を確かめ、件名つきの描いた結果にする。
     *
     * @param templateId テンプレートの識別
     * @param language 依頼の言語
     * @param html 描いた本文
     * @return 描いた結果。title が無い・件名が空・lang が無いか違うときは空（TEMPLATE_ERROR）
     */
    public static Optional<RenderedMail> inspect(String templateId, String language, String html) {
        Optional<String> subject = subject(html);
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        if (!lang(html).map(language::equals).orElse(false)) {
            return Optional.empty();
        }
        return Optional.of(new RenderedMail(templateId, language, subject.get(), html));
    }

    /**
     * 最初の title 要素の文面から件名を作る（文字参照を戻し、空白と改行を1つの空白にまとめ、前後を除く）。
     *
     * @param html 描いた本文
     * @return 件名。title が無い・空なら空
     */
    static Optional<String> subject(String html) {
        Matcher matcher = TITLE.matcher(html);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String text = HtmlUtils.htmlUnescape(matcher.group(1));
        String subject = WHITESPACE.matcher(text).replaceAll(" ").strip();
        return subject.isEmpty() ? Optional.empty() : Optional.of(subject);
    }

    /**
     * 最初の html 要素の lang 属性の値を返す。
     *
     * @param html 描いた本文
     * @return lang 属性の値。html 要素か lang 属性が無ければ空
     */
    static Optional<String> lang(String html) {
        Matcher tag = HTML_TAG.matcher(html);
        if (!tag.find()) {
            return Optional.empty();
        }
        Matcher attribute = LANG.matcher(tag.group());
        if (!attribute.find()) {
            return Optional.empty();
        }
        for (int group = 1; group <= 3; group++) {
            if (attribute.group(group) != null) {
                return Optional.of(attribute.group(group));
            }
        }
        return Optional.empty();
    }
}
