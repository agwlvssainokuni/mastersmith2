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
import cherry.mastersmith.common.security.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * フィルターの段階のエラー応答を書く（BR5.1）。{@link ErrorResponseWriter} の U1 の実装。
 *
 * <p>本文は {@link ErrorResponseFactory} で組み立て、Spring MVC と同じメッセージの変換器で {@code application/problem+json} に
 * 書くため、コントローラーの中の例外の変換と同じ形になる。
 */
@Component
public class DefaultErrorResponseWriter implements ErrorResponseWriter {

    private final ErrorResponseFactory factory;

    private final Supplier<List<HttpMessageConverter<?>>> converters;

    /**
     * Spring MVC の変換器を使う書き方を作る。
     *
     * @param factory ErrorResponse の組み立て
     * @param handlerAdapter Spring MVC の要求の処理（変換器の取得に使う。遅れて取得する）
     */
    @Autowired
    public DefaultErrorResponseWriter(
            ErrorResponseFactory factory, ObjectProvider<RequestMappingHandlerAdapter> handlerAdapter) {
        this(factory, () -> handlerAdapter.getObject().getMessageConverters());
    }

    /**
     * 変換器を指定して書き方を作る（テストで使う）。
     *
     * @param factory ErrorResponse の組み立て
     * @param converters メッセージの変換器
     */
    DefaultErrorResponseWriter(ErrorResponseFactory factory, Supplier<List<HttpMessageConverter<?>>> converters) {
        this.factory = factory;
        this.converters = converters;
    }

    @Override
    public void write(HttpServletRequest request, HttpServletResponse response, ProblemType problemType)
            throws IOException {
        ProblemDetail body = factory.create(request, problemType, null);
        ServletServerHttpResponse output = new ServletServerHttpResponse(response);
        output.setStatusCode(HttpStatusCode.valueOf(problemType.status()));
        for (HttpMessageConverter<?> converter : converters.get()) {
            if (converter.canWrite(ProblemDetail.class, MediaType.APPLICATION_PROBLEM_JSON)) {
                @SuppressWarnings("unchecked")
                HttpMessageConverter<Object> writer = (HttpMessageConverter<Object>) converter;
                writer.write(body, MediaType.APPLICATION_PROBLEM_JSON, output);
                output.flush();
                return;
            }
        }
        throw new IllegalStateException("application/problem+json を書ける変換器がありません");
    }
}
