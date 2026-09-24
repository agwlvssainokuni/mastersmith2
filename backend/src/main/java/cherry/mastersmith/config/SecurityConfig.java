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
package cherry.mastersmith.config;

import cherry.mastersmith.common.security.ApiDefaultAccess;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import cherry.mastersmith.common.security.SecurityExtensionValidator;
import cherry.mastersmith.common.security.SecurityRuleContributor;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import cherry.mastersmith.common.web.RequestBodyLimitRoute;
import cherry.mastersmith.common.web.RequestSizeLimitFilter;
import cherry.mastersmith.common.web.RequestSizeRejectionListener;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Spring Security のフィルターの連鎖（1つだけ）と、U2・U3 が決まりを足す差し込み口（security-design 2章・3章）。
 *
 * <p>状態を持たない（セッションを作らない）。フォームのログイン・Basic 認証・CSRF の仕組みは使わない。要求の検査（正規化されて
 * いないパスの拒否）は既定のまま使う。すべての応答に Content-Security-Policy などのヘッダーを付け、Spring Security の既定の
 * キャッシュの指定は外す（{@code CacheControlFilter} が付ける）。
 *
 * <p>アクセスの決まりの並び（先に当たった決まりが使われる）:
 *
 * <ol>
 *   <li>U1 の公開の決まり: {@code /actuator/health}、{@code /api/problems/**} は認証を求めない
 *   <li>{@link SecurityRuleContributor} を order の小さい順（U2 は 100 台、U3 は 200 台）
 *   <li>{@link ApiDefaultAccess} による {@code /api/**} の既定（無ければ許可）
 *   <li>{@code /api/**} 以外（画面の配信）は許可
 * </ol>
 *
 * <p>ヘッダー・セッション・CSRF の設定は U1 だけが決め、U2・U3 は変えない。
 *
 * <p>要求の本文の大きさの確かめ（{@link RequestSizeLimitFilter}）は、認証（アクセストークンの検証）と認可（{@link AuthorizationFilter}）
 * の後に置く（Intent 260923-dsl-schema-loader の U4 の NFR 設計の決定 A）。本文を読むのはログインしていてその道を使える人の要求
 * だけになり、ログインしていない大きな要求は 413 ではなく 401 になる。道ごとの上限（{@link RequestBodyLimitRoute}）と、断ったことの
 * 受け取り（{@link RequestSizeRejectionListener}）は各機能が Bean として置く。
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * フィルターの連鎖を作る。
     *
     * @param http フィルターの連鎖の設定
     * @param contributors 追加の決まり（U2・U3）
     * @param defaultAccesses API の既定の扱い（U3、0個か1個）
     * @param headerProperties 応答のヘッダーの設定
     * @param webProperties Web の設定
     * @param bodyLimitRoutes 本文の上限の道ごとの決まり（各機能）
     * @param sizeRejectionListeners 本文の上限で断ったことの受け取り（各機能）
     * @param errorResponseWriter フィルターの段階のエラー応答の書き方
     * @return フィルターの連鎖
     * @throws Exception 設定に失敗したとき、または差し込み口の検査に失敗したとき
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            List<SecurityRuleContributor> contributors,
            List<ApiDefaultAccess> defaultAccesses,
            SecurityHeaderProperties headerProperties,
            MastersmithWebProperties webProperties,
            List<RequestBodyLimitRoute> bodyLimitRoutes,
            List<RequestSizeRejectionListener> sizeRejectionListeners,
            ErrorResponseWriter errorResponseWriter)
            throws Exception {
        List<SecurityRuleContributor> sortedContributors = SecurityExtensionValidator.sortedContributors(contributors);
        Optional<ApiDefaultAccess> defaultAccess = SecurityExtensionValidator.singleDefaultAccess(defaultAccesses);

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives(headerProperties.contentSecurityPolicy()))
                        .contentTypeOptions(options -> {})
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        .cacheControl(cache -> cache.disable())
                        .httpStrictTransportSecurity(hsts -> hsts.disable()))
                // U3 が入口の処理を足すまでは、ログインが必要な要求に本文なしの 401 を返す（U1 だけでは起きない）。
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterAfter(
                        new RequestSizeLimitFilter(
                                webProperties.maxRequestBodySize().toBytes(),
                                bodyLimitRoutes,
                                sizeRejectionListeners,
                                errorResponseWriter),
                        AuthorizationFilter.class);

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/api/problems/**")
                .permitAll());

        for (SecurityRuleContributor contributor : sortedContributors) {
            contributor.contribute(http);
        }

        boolean apiRequiresAuthentication =
                defaultAccess.map(ApiDefaultAccess::requireAuthentication).orElse(false);
        http.authorizeHttpRequests(authorize -> {
            if (apiRequiresAuthentication) {
                authorize.requestMatchers("/api/**").authenticated();
            } else {
                authorize.requestMatchers("/api/**").permitAll();
            }
            authorize.anyRequest().permitAll();
        });
        return http.build();
    }
}
