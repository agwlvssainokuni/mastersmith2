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
package cherry.mastersmith.dslmanage.web;

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.web.ClientInfoResolver;
import cherry.mastersmith.common.i18n.domain.AcceptLanguageResolver;
import cherry.mastersmith.dslmanage.service.DslRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 要求から DSL の操作の文脈（操作した管理者・送り手・表示言語）を作る。
 *
 * <p>操作した管理者は、認証の結果の主体（{@link AuthenticatedUser}）から取る。送り手は既存の {@link ClientInfoResolver}、表示言語は
 * 既存の {@link AcceptLanguageResolver}（Accept-Language）で決める。
 */
@Component
public class DslRequestContextResolver {

    private final ClientInfoResolver clientInfoResolver;

    /**
     * 作る。
     *
     * @param clientInfoResolver 送り手の情報の組み立て
     */
    public DslRequestContextResolver(ClientInfoResolver clientInfoResolver) {
        this.clientInfoResolver = clientInfoResolver;
    }

    /**
     * 認証の結果から、操作した管理者を返す。
     *
     * @return 操作した管理者（認証されていなければ空）
     */
    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    /**
     * 要求の文脈を作る。
     *
     * @param request 要求
     * @param user 操作した管理者
     * @return 要求の文脈
     */
    public DslRequestContext resolve(HttpServletRequest request, AuthenticatedUser user) {
        ClientInfo client = clientInfoResolver.resolve(request);
        return new DslRequestContext(
                user.userId(),
                client.sourceIp(),
                client.userAgent(),
                client.traceId(),
                AcceptLanguageResolver.resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)));
    }
}
