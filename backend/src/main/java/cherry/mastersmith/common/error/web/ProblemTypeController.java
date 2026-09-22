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

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.common.i18n.domain.AcceptLanguageResolver;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 問題の種類の説明ページ（{@code GET /api/problems/{slug}}、BR5.11〜BR5.13）。ログインを求めない。
 *
 * <p>要求が HTML を優先して求めていれば HTML、そうでなければ JSON で返す。言語は Accept-Language で決める（BR6.3）。
 * 定義の無い slug は 404 / {@code NOT_FOUND}。
 */
@RestController
public class ProblemTypeController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final ProblemTypeRegistry registry;

    private final ProblemTypeHtmlRenderer renderer;

    /**
     * 説明ページを作る。
     *
     * @param registry 問題の種類の一覧
     * @param renderer 説明ページの HTML
     */
    public ProblemTypeController(ProblemTypeRegistry registry, ProblemTypeHtmlRenderer renderer) {
        this.registry = registry;
        this.renderer = renderer;
    }

    /**
     * 問題の種類の説明を返す。
     *
     * @param slug 問題の種類の slug
     * @param accept Accept ヘッダー
     * @param acceptLanguage Accept-Language ヘッダー
     * @return 説明（HTML または JSON）
     */
    @GetMapping(ErrorResponseFactory.PROBLEMS_PATH + "{slug}")
    public ResponseEntity<?> describe(
            @PathVariable String slug,
            @RequestHeader(name = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProblemType type =
                registry.findBySlug(slug).orElseThrow(() -> new BusinessException(CommonProblemTypes.NOT_FOUND));
        DisplayLanguage language = AcceptLanguageResolver.resolve(acceptLanguage);
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok().header(HttpHeaders.CONTENT_LANGUAGE, language.tag());
        if (prefersHtml(accept)) {
            return ok.contentType(HTML_UTF8).body(renderer.render(type, language));
        }
        return ok.contentType(MediaType.APPLICATION_JSON).body(ProblemTypeResponse.of(type, language));
    }

    /**
     * Accept の中で、HTML が JSON より優先されているかを判定する。ワイルドカードだけのときは JSON とする。
     *
     * @param accept Accept ヘッダー
     * @return HTML を優先していれば true
     */
    static boolean prefersHtml(String accept) {
        if (accept == null || accept.isBlank()) {
            return false;
        }
        List<MediaType> types;
        try {
            types = new ArrayList<>(MediaType.parseMediaTypes(accept));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
        types.removeIf(type -> type.getQualityValue() <= 0);
        // q 値の大きい順。同じ q 値は書かれた順（並べ替えは安定）。
        types.sort(Comparator.comparingDouble(MediaType::getQualityValue).reversed());
        for (MediaType type : types) {
            if (type.isWildcardType()) {
                return false;
            }
            if (type.isCompatibleWith(MediaType.TEXT_HTML)) {
                return true;
            }
            if (type.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                return false;
            }
        }
        return false;
    }
}
