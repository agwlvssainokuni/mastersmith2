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
package cherry.mastersmith.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 応答のキャッシュの指定を付ける（NFR3.10、performance-design 5章）。Spring Security の既定の指定の代わりに使う。
 *
 * <ul>
 *   <li>{@code /api/**}・{@code /actuator/**}: {@code no-store}（利用者ごとの応答を保存させない）
 *   <li>{@code /assets/**}（名前にハッシュが付く画面のファイル）: {@code public, max-age=31536000, immutable}
 *   <li>それ以外（{@code index.html} と画面の URL への応答）: {@code no-cache}
 * </ul>
 *
 * <p>フィルターの連鎖で拒否された応答（413 など）にも付くよう、Spring Security のフィルターの連鎖より前に通す。
 */
@Component
public class CacheControlFilter extends OncePerRequestFilter implements Ordered {

    /** API と Actuator の応答の指定。 */
    static final String NO_STORE = "no-store";

    /** 名前にハッシュが付く画面のファイルの指定。 */
    static final String IMMUTABLE = "public, max-age=31536000, immutable";

    /** 画面の入口の指定。 */
    static final String NO_CACHE = "no-cache";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControlFor(pathWithinApplication(request)));
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /**
     * パスに対応するキャッシュの指定を返す。
     *
     * @param path アプリの中のパス
     * @return Cache-Control の値
     */
    static String cacheControlFor(String path) {
        if (startsWithSegment(path, "/api") || startsWithSegment(path, "/actuator")) {
            return NO_STORE;
        }
        if (path.startsWith("/assets/")) {
            return IMMUTABLE;
        }
        return NO_CACHE;
    }

    private static boolean startsWithSegment(String path, String segment) {
        return path.equals(segment) || path.startsWith(segment + "/");
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    @Override
    public int getOrder() {
        return SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1;
    }
}
