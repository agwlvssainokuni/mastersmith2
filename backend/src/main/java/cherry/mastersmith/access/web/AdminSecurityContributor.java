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
package cherry.mastersmith.access.web;

import cherry.mastersmith.access.domain.AdminPaths;
import cherry.mastersmith.common.security.SecurityRuleContributor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * U3 がフィルターの連鎖に足す決まり（order 210。計画の C1）。
 *
 * <ul>
 *   <li>{@code /api/admin} と {@code /api/admin/**} を管理者のみにする（BR1.1、BR1.6）
 *   <li>401 の入口の処理を、Bearer の検証の失敗と未認証の両方について U3 のものに置き換える（U2 の order 110 の設定を、後から
 *       当たるこの設定で置き換える。計画の D2）
 *   <li>403 のアクセス拒否の処理を U3 のものにする（BR3.6）
 * </ul>
 *
 * <p>ヘッダー・セッション・CSRF の設定は変えない（U1 だけが決める）。
 */
@Component
public class AdminSecurityContributor implements SecurityRuleContributor {

    /** この決まりの順番（U3 は 200 台。200 は U1 のテストの役の決まりが使う）。 */
    public static final int ORDER = 210;

    private final AdminAuthenticationEntryPoint authenticationEntryPoint;

    private final AdminAccessDeniedHandler accessDeniedHandler;

    /**
     * 作る。
     *
     * @param authenticationEntryPoint 401 の入口の処理
     * @param accessDeniedHandler 403 のアクセス拒否の処理
     */
    public AdminSecurityContributor(
            AdminAuthenticationEntryPoint authenticationEntryPoint, AdminAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void contribute(HttpSecurity http) throws Exception {
        AdminAuthorizationManager authorizationManager = new AdminAuthorizationManager();
        http.authorizeHttpRequests(authorize ->
                authorize.requestMatchers(AdminPaths.adminPatterns()).access(authorizationManager));
        http.oauth2ResourceServer(resourceServer -> resourceServer.authenticationEntryPoint(authenticationEntryPoint));
        http.exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler));
    }
}
