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
package cherry.mastersmith.auth.web;

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 の入口の処理（BR4.4、NFR10.5、計画の D2）。U1 の {@link ErrorResponseWriter} で 401 / {@code AUTHENTICATION_REQUIRED}
 * を書き、区分（{@link TokenAuthenticationException} ならその区分、そうでなければ {@code TOKEN_MISSING}）を DEBUG で出す。トーク
 * ンの値は出さない。U3 が order 200 台の決まりで置き換える。
 */
@Component
public class TokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** トークンが無い要求の区分。 */
    static final String TOKEN_MISSING = "TOKEN_MISSING";

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenAuthenticationEntryPoint.class);

    private final ErrorResponseWriter errorResponseWriter;

    /**
     * 作る。
     *
     * @param errorResponseWriter フィルターの段階のエラー応答の書き方
     */
    public TokenAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String reason = authException instanceof TokenAuthenticationException token
                ? token.reason().name()
                : TOKEN_MISSING;
        LOGGER.atDebug().addKeyValue("reason", reason).log("アクセストークンの認証に失敗しました");
        errorResponseWriter.write(request, response, AuthProblemTypes.AUTHENTICATION_REQUIRED);
    }
}
