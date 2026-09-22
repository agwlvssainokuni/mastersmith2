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

import cherry.mastersmith.auth.domain.AccessTokenValue;
import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import cherry.mastersmith.auth.service.AccessTokenService;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

/**
 * Bearer のアクセストークンの検証（WF3、BR4.3〜BR4.5）。{@link AccessTokenService} で検証し、利用者を DB から読んで
 * {@link AuthenticatedUser}（管理者は DB の値）を主体とする認証の結果を作る。利用者がいなければ {@code USER_NOT_FOUND}。
 *
 * <p>Bean にせず {@link AuthSecurityContributor} の中で作る（メソッドの呼び出しの追跡がトークンを含む引数を文字列にしないため）。
 */
public class AccessTokenAuthenticationProvider implements AuthenticationProvider {

    private final AccessTokenService accessTokenService;

    private final UserAccountService userAccountService;

    /**
     * 作る。
     *
     * @param accessTokenService アクセストークンの検証
     * @param userAccountService 利用者の読み取り
     */
    public AccessTokenAuthenticationProvider(
            AccessTokenService accessTokenService, UserAccountService userAccountService) {
        this.accessTokenService = accessTokenService;
        this.userAccountService = userAccountService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String token = ((BearerTokenAuthenticationToken) authentication).getToken();
        long userId = accessTokenService.verify(new AccessTokenValue(token));
        UserSummary user = userAccountService
                .findById(userId)
                .orElseThrow(() -> new TokenAuthenticationException(TokenFailureReason.USER_NOT_FOUND));
        return new AuthenticatedUserToken(new AuthenticatedUser(user.userId(), user.email(), user.admin()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
