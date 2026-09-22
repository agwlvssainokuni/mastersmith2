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

import cherry.mastersmith.auth.service.AccessTokenService;
import cherry.mastersmith.common.security.SecurityRuleContributor;
import cherry.mastersmith.user.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * U2 がフィルターの連鎖に足す決まり（order 110、計画の C3・D2）。
 *
 * <ul>
 *   <li>{@code /api/auth/login} と {@code /api/auth/session/**} は認証なし
 *   <li>OAuth2 Resource Server の Bearer の検証。トークンは {@code Authorization: Bearer} のヘッダーからだけ取り出し、
 *       {@code /api/auth/**} では取り出さない（期限切れのトークンが付いていても、ログイン・更新・ログアウトを妨げない）
 *   <li>検証の失敗と、ログインが必要な要求の未認証の両方の入口に {@link TokenAuthenticationEntryPoint} を置く
 * </ul>
 *
 * <p>ヘッダー・セッション・CSRF の設定は変えない（U1 だけが決める）。
 */
@Component
public class AuthSecurityContributor implements SecurityRuleContributor {

    /** この決まりの順番（U2 は 100 台。100 は U1 のテストの役の決まりが使う）。 */
    public static final int ORDER = 110;

    private final AuthenticationManager authenticationManager;

    private final TokenAuthenticationEntryPoint entryPoint;

    /**
     * 作る。
     *
     * @param accessTokenService アクセストークンの検証
     * @param userAccountService 利用者の読み取り
     * @param entryPoint 401 の入口の処理
     */
    public AuthSecurityContributor(
            AccessTokenService accessTokenService,
            UserAccountService userAccountService,
            TokenAuthenticationEntryPoint entryPoint) {
        this.authenticationManager =
                new ProviderManager(new AccessTokenAuthenticationProvider(accessTokenService, userAccountService));
        this.entryPoint = entryPoint;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void contribute(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/login", "/api/auth/session/**")
                .permitAll());
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .bearerTokenResolver(bearerTokenResolver())
                .authenticationManagerResolver(request -> authenticationManager)
                .authenticationEntryPoint(entryPoint));
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
    }

    /**
     * Authorization ヘッダーからだけトークンを取り出し、{@code /api/auth/**} では取り出さない。
     *
     * @return トークンの取り出し
     */
    static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        header.setAllowFormEncodedBodyParameter(false);
        header.setAllowUriQueryParameter(false);
        return (HttpServletRequest request) -> isAuthApi(request) ? null : header.resolve(request);
    }

    private static boolean isAuthApi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/auth/");
    }
}
