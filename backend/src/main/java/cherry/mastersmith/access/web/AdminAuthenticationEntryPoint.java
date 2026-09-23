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

import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.access.domain.AdminPaths;
import cherry.mastersmith.access.service.AccessDeniedEventPublisher;
import cherry.mastersmith.auth.web.ClientInfoResolver;
import cherry.mastersmith.auth.web.TokenAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 の入口の処理（BR2.1、BR3.2、BR3.3、BR3.6。計画の D2）。U1 の連鎖には U3 の決まり（order 210）で置く。
 *
 * <p>U3 が受け持つのは、管理者のみのパスかどうかの判断・理由の判定・出来事の通知だけである。応答の書き出しと区分の DEBUG の
 * ログは U2 の {@link TokenAuthenticationEntryPoint} に任せる（401 の応答の形を1か所に保ち、U2 のソースとテストを変えない
 * ため）。
 *
 * <p>管理者のみのパスで、理由が有効期限切れでないときだけ出来事を知らせる（BR3.2）。管理者のみ以外のパスの 401 は知らせない
 * （BR3.3）。出来事の通知で例外が戻っても応答は変わらない（通知の側で受け止める）。
 */
@Component
public class AdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final TokenAuthenticationEntryPoint delegate;

    private final AccessDeniedEventPublisher eventPublisher;

    private final ClientInfoResolver clientInfoResolver;

    private final Clock clock;

    /**
     * 作る。
     *
     * @param delegate 401 の応答の書き出しと区分の DEBUG（U2）
     * @param eventPublisher アクセス拒否の出来事の通知
     * @param clientInfoResolver 送り手の情報の取り出し（U2）
     * @param clock 現在時刻の時計（U2 の Bean）
     */
    public AdminAuthenticationEntryPoint(
            TokenAuthenticationEntryPoint delegate,
            AccessDeniedEventPublisher eventPublisher,
            ClientInfoResolver clientInfoResolver,
            Clock clock) {
        this.delegate = delegate;
        this.eventPublisher = eventPublisher;
        this.clientInfoResolver = clientInfoResolver;
        this.clock = clock;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String path = AdminPaths.relativePath(request.getRequestURI(), request.getContextPath());
        if (AdminPaths.isAdminOnly(path)) {
            AccessDeniedReason.of(authException)
                    .ifPresent(reason -> eventPublisher.publish(AdminAccessDeniedEvent.of(
                            clock.instant(), reason, null, path, clientInfoResolver.resolve(request))));
        }
        delegate.commence(request, response, authException);
    }
}
