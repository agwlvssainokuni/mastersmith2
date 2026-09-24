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
import cherry.mastersmith.common.i18n.domain.AcceptLanguageResolver;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.common.observability.TraceIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * ErrorResponse（RFC 9457 Problem Details に {@code code} と {@code traceId} を足したもの）を組み立てる（BR5.1〜BR5.4、BR5.9）。
 *
 * <p>コントローラーの中の例外の変換（{@link GlobalExceptionHandler}）と、フィルターの段階の応答（{@link DefaultErrorResponseWriter}）の
 * 両方がこれを使い、同じ形にする。{@code detail} には利用者に見せてよい説明だけを入れ、無ければ問題の種類の説明を入れる。
 * 例外のメッセージは入れない。
 */
@Component
public class ErrorResponseFactory {

    /** 説明ページの URL のパス。 */
    public static final String PROBLEMS_PATH = "/api/problems/";

    /** 追加の項目で上書きさせない項目名（Problem Details の標準の項目と {@code code}・{@code traceId}）。 */
    static final Set<String> RESERVED_PROPERTIES =
            Set.of("type", "title", "status", "detail", "instance", "code", "traceId");

    private final ProblemBaseUrlResolver baseUrlResolver;

    private final TraceIdProvider traceIdProvider;

    /**
     * 組み立ての仕組みを作る。
     *
     * @param baseUrlResolver ベースURLの決め方
     * @param traceIdProvider 要求中のトレースIDの参照
     */
    public ErrorResponseFactory(ProblemBaseUrlResolver baseUrlResolver, TraceIdProvider traceIdProvider) {
        this.baseUrlResolver = baseUrlResolver;
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * ErrorResponse を組み立てる。
     *
     * @param request 要求
     * @param type 問題の種類
     * @param detail 利用者に見せてよい説明（無ければ null。問題の種類の説明を使う）
     * @return ErrorResponse
     */
    public ProblemDetail create(HttpServletRequest request, ProblemType type, String detail) {
        return create(request, type, detail, Map.of());
    }

    /**
     * 追加の項目を持つ ErrorResponse を組み立てる（Intent 260923-dsl-schema-loader の U4、BR8.1）。
     *
     * <p>追加の項目は、既存の項目名（{@link #RESERVED_PROPERTIES}）を上書きしない（その名前の項目は載せない）。
     *
     * @param request 要求
     * @param type 問題の種類
     * @param detail 利用者に見せてよい説明（無ければ null。問題の種類の説明を使う）
     * @param properties 追加の項目（名前と値）
     * @return ErrorResponse
     */
    public ProblemDetail create(
            HttpServletRequest request, ProblemType type, String detail, Map<String, Object> properties) {
        DisplayLanguage language = AcceptLanguageResolver.resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        ProblemDetail problem = ProblemDetail.forStatus(type.status());
        problem.setType(URI.create(baseUrlResolver.resolve(request) + PROBLEMS_PATH + type.slug()));
        problem.setTitle(type.title().in(language));
        problem.setDetail(
                detail != null && !detail.isBlank()
                        ? detail
                        : type.description().in(language));
        URI instance = toInstance(request.getRequestURI());
        if (instance != null) {
            problem.setInstance(instance);
        }
        properties.forEach((name, value) -> {
            if (!RESERVED_PROPERTIES.contains(name)) {
                problem.setProperty(name, value);
            }
        });
        problem.setProperty("code", type.code());
        traceIdProvider.currentTraceId().ifPresent(traceId -> problem.setProperty("traceId", traceId));
        return problem;
    }

    /** 要求のパスを instance にする。URI として正しくなければ載せない。 */
    private static URI toInstance(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return new URI(path);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
