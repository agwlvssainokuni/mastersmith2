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
package cherry.mastersmith.invitation.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.user.domain.RequestOrigin;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 要求の文脈の読み取り（管理者の利用者 ID と送り手の情報。{@code security-design.md} 2節、U2 の {@code MeRequestContextResolver} と
 * 同じ形）。{@code invitation} は {@code auth} に依存しないため、401 の問題の種類は共通の一覧から code で起動時に1回だけ引き、無ければ
 * 起動を止める。
 *
 * <ul>
 *   <li>管理者の利用者 ID: Spring Security の標準の {@link Authentication#getName()} を数として読む。認証が無い・匿名・数として読めない
 *       ときは 401 {@code AUTHENTICATION_REQUIRED}（想定外の経路の守り）
 *   <li>送り手の情報: 既存の認証の送り手の情報と同じ取り方（接続元IPは {@code request.getRemoteAddr()}、User-Agent は 512 文字で切る、
 *       トレースIDは共通の仕組み）。登録の完了（ログインなし）でも同じ部品で作る
 * </ul>
 */
@Component
public class InvitationRequestContextResolver {

    /** 401 の問題の種類の code（{@code auth} の一覧にある）。 */
    static final String AUTHENTICATION_REQUIRED_CODE = "AUTHENTICATION_REQUIRED";

    private final ProblemType authenticationRequired;

    private final TraceIdProvider traceIdProvider;

    /**
     * 作る。
     *
     * @param registry 問題の種類の一覧
     * @param traceIdProvider 要求中のトレースIDの参照
     * @throws IllegalStateException 401 の問題の種類が一覧に無いとき（起動を止める）
     */
    public InvitationRequestContextResolver(ProblemTypeRegistry registry, TraceIdProvider traceIdProvider) {
        this.authenticationRequired = registry.findByCode(AUTHENTICATION_REQUIRED_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "問題の種類 " + AUTHENTICATION_REQUIRED_CODE + " が一覧にありません（認証の機能の定義が要ります）"));
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * 操作した管理者の利用者 ID を返す。
     *
     * @return 利用者 ID
     * @throws BusinessException 認証が無い・匿名・利用者 ID として読めないとき（401 {@code AUTHENTICATION_REQUIRED}）
     */
    public long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(authenticationRequired);
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(authenticationRequired);
        }
    }

    /**
     * 要求から送り手の情報を作る。
     *
     * @param request 要求
     * @return 送り手の情報
     */
    public RequestOrigin origin(HttpServletRequest request) {
        return new RequestOrigin(
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                traceIdProvider.currentTraceId().orElse(null));
    }
}
