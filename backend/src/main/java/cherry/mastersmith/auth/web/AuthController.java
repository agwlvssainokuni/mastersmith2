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

import cherry.mastersmith.auth.service.IssuedTokens;
import cherry.mastersmith.auth.service.LoginCommand;
import cherry.mastersmith.auth.service.LoginService;
import cherry.mastersmith.auth.service.LogoutService;
import cherry.mastersmith.auth.service.TokenRefreshService;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.user.domain.Password;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証の API（security-design 3章、計画の C2）。エラーは業務エラーを起こし、U1 の共通の変換で応答にする。
 *
 * <ul>
 *   <li>{@code POST /api/auth/login}: 200 と {@link TokenResponse}、リフレッシュトークンの Cookie
 *   <li>{@code POST /api/auth/session/refresh}: Origin の確認、200 と {@link TokenResponse}、新しい Cookie。失敗は 401 /
 *       {@code REFRESH_FAILED} と Cookie の削除
 *   <li>{@code POST /api/auth/session/logout}: Origin の確認、204 と Cookie の削除（Cookie が無い・無効でも同じ）
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;

    private final TokenRefreshService refreshService;

    private final LogoutService logoutService;

    private final RefreshCookies cookies;

    private final ClientInfoResolver clientInfoResolver;

    private final OriginVerifier originVerifier;

    /**
     * 作る。
     *
     * @param loginService ログインの処理
     * @param refreshService 更新の処理
     * @param logoutService ログアウトの処理
     * @param cookies リフレッシュトークンの Cookie
     * @param clientInfoResolver 送り手の情報の取り出し
     * @param originVerifier Origin の確認
     */
    public AuthController(
            LoginService loginService,
            TokenRefreshService refreshService,
            LogoutService logoutService,
            RefreshCookies cookies,
            ClientInfoResolver clientInfoResolver,
            OriginVerifier originVerifier) {
        this.loginService = loginService;
        this.refreshService = refreshService;
        this.logoutService = logoutService;
        this.cookies = cookies;
        this.clientInfoResolver = clientInfoResolver;
        this.originVerifier = originVerifier;
    }

    /**
     * ログインする。
     *
     * @param body メールアドレスとパスワード
     * @param request 要求
     * @param response 応答（Cookie を付ける）
     * @return アクセストークンと利用者
     */
    @PostMapping(
            path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        IssuedTokens tokens = loginService.login(
                new LoginCommand(body.email(), new Password(body.password())), clientInfoResolver.resolve(request));
        cookies.issue(response, tokens.refreshToken(), tokens.refreshTokenTtl());
        return TokenResponse.from(tokens);
    }

    /**
     * Cookie のリフレッシュトークンで更新する。失敗のときは、例外を起こす前に Cookie を消す指示を応答に入れる。
     *
     * @param request 要求
     * @param response 応答（Cookie を付ける・消す）
     * @return 新しいアクセストークンと利用者
     */
    @PostMapping(path = "/session/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        originVerifier.verify(request);
        IssuedTokens tokens;
        try {
            tokens = refreshService.refresh(cookies.read(request));
        } catch (BusinessException e) {
            cookies.clear(response);
            throw e;
        }
        cookies.issue(response, tokens.refreshToken(), tokens.refreshTokenTtl());
        return TokenResponse.from(tokens);
    }

    /**
     * ログアウトする（アクセストークンは失効させない。BR4.6）。
     *
     * @param request 要求
     * @param response 応答（Cookie を消す）
     * @return 204（内容なし）
     */
    @PostMapping("/session/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        originVerifier.verify(request);
        logoutService.logout(cookies.read(request), clientInfoResolver.resolve(request));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
