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

import cherry.mastersmith.auth.domain.RefreshTokenValue;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークンの Cookie（NFR5.3、BR5.2、security-design 3章）。
 *
 * <p>名前 {@code mastersmith_refresh}、{@code HttpOnly}・{@code Secure}・{@code SameSite=Strict}・
 * {@code Path=/api/auth/session}・{@code Max-Age}＝有効期限。削除は同じ名前・同じ Path で {@code Max-Age=0}。
 */
@Component
public class RefreshCookies {

    /** Cookie の名前。 */
    public static final String NAME = "mastersmith_refresh";

    /** Cookie を送る先（更新とログアウトの API だけ）。 */
    public static final String PATH = "/api/auth/session";

    /**
     * リフレッシュトークンの Cookie を応答に付ける。
     *
     * @param response 応答
     * @param value トークンの値
     * @param ttl 有効期限の長さ
     */
    public void issue(HttpServletResponse response, RefreshTokenValue value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(value.value(), ttl).toString());
    }

    /**
     * Cookie を消す指示を応答に付ける。
     *
     * @param response 応答
     */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /**
     * 要求の Cookie からトークンの値を読む。
     *
     * @param request 要求
     * @return トークンの値（無ければ null）
     */
    public RefreshTokenValue read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return new RefreshTokenValue(cookie.getValue());
            }
        }
        return null;
    }

    private static ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
