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
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 問題の種類の説明ページの HTML を作る（BR5.11、BR5.15、NFR3.9）。
 *
 * <p>決まった形のひな形に、問題の種類の定義の値だけを HTML としてエスケープして埋め込む。要求から受け取った値（slug を含む）を
 * 受け取る口は持たない。埋め込みのスタイル・スクリプトは持たない（CSP の {@code 'self'} に合わせる）。
 */
@Component
public class ProblemTypeHtmlRenderer {

    /**
     * 説明ページの HTML を作る。
     *
     * @param type 問題の種類
     * @param language 表示言語
     * @return HTML
     */
    public String render(ProblemType type, DisplayLanguage language) {
        boolean en = language == DisplayLanguage.EN;
        String title = escape(type.title().in(language));
        StringBuilder html = new StringBuilder(1024)
                .append("<!DOCTYPE html>\n")
                .append("<html lang=\"")
                .append(language.tag())
                .append("\">\n")
                .append("<head>\n<meta charset=\"UTF-8\">\n<title>")
                .append(title)
                .append("</title>\n</head>\n<body>\n<main>\n<h1>")
                .append(title)
                .append("</h1>\n<dl>\n");
        item(html, en ? "Code" : "コード", escape(type.code()));
        item(html, en ? "HTTP status" : "状態コード", Integer.toString(type.status()));
        item(html, en ? "When it occurs" : "起きるとき", escape(type.description().in(language)));
        if (type.resolution() != null) {
            item(html, en ? "What to do" : "すべきこと", escape(type.resolution().in(language)));
        }
        return html.append("</dl>\n</main>\n</body>\n</html>\n").toString();
    }

    private static void item(StringBuilder html, String label, String escapedValue) {
        html.append("<dt>")
                .append(label)
                .append("</dt>\n<dd>")
                .append(escapedValue)
                .append("</dd>\n");
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
