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
import cherry.mastersmith.access.domain.AccessProblemTypes;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.access.domain.AdminPaths;
import cherry.mastersmith.access.service.AccessDeniedEventPublisher;
import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.auth.web.ClientInfoResolver;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 403 のアクセス拒否の処理（BR2.2、BR3.1、BR3.6）。U1 の {@link ErrorResponseWriter} で 403 / {@code ACCESS_DENIED} を
 * 共通の形で書く。
 *
 * <p>管理者のみのパスのときは、理由 {@link AccessDeniedReason#NOT_ADMIN} の出来事を、その利用者のメールアドレスを添えて
 * 知らせる（BR3.1）。出来事の通知で例外が戻っても応答は変わらない（通知の側で受け止める）。
 *
 * <p>WARN には {@code code} だけを出し、メールアドレス・パス・トークンを出さない（NFR10.3）。
 */
@Component
public class AdminAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccessDeniedHandler.class);

    private final ErrorResponseWriter errorResponseWriter;

    private final AccessDeniedEventPublisher eventPublisher;

    private final ClientInfoResolver clientInfoResolver;

    private final Clock clock;

    /**
     * 作る。
     *
     * @param errorResponseWriter フィルターの段階のエラー応答の書き方（U1）
     * @param eventPublisher アクセス拒否の出来事の通知
     * @param clientInfoResolver 送り手の情報の取り出し（U2）
     * @param clock 現在時刻の時計（U2 の Bean）
     */
    public AdminAccessDeniedHandler(
            ErrorResponseWriter errorResponseWriter,
            AccessDeniedEventPublisher eventPublisher,
            ClientInfoResolver clientInfoResolver,
            Clock clock) {
        this.errorResponseWriter = errorResponseWriter;
        this.eventPublisher = eventPublisher;
        this.clientInfoResolver = clientInfoResolver;
        this.clock = clock;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        String path = AdminPaths.relativePath(request.getRequestURI(), request.getContextPath());
        if (AdminPaths.isAdminOnly(path)) {
            eventPublisher.publish(AdminAccessDeniedEvent.of(
                    clock.instant(),
                    AccessDeniedReason.NOT_ADMIN,
                    currentEmail(),
                    path,
                    clientInfoResolver.resolve(request)));
        }
        LOGGER.atWarn()
                .addKeyValue("code", AccessProblemTypes.ACCESS_DENIED.code())
                .log("権限が足りないため要求を拒否しました");
        errorResponseWriter.write(request, response, AccessProblemTypes.ACCESS_DENIED);
    }

    /** ログイン中の利用者のメールアドレス（分からなければ null）。 */
    private static String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.email();
        }
        return null;
    }
}
