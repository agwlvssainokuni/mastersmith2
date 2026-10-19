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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 業務データの API の要求の文脈から、利用者 ID を読む（本文から利用者を受け取らない。functional-spec.md の 3.1節の 1）。
 *
 * <p>businessdata は auth に依存しないため、role の {@code RoleRequestContextResolver} と同じ形の部品を持つ（認証の結果の名前が利用者
 * ID）。
 */
@Component
public class BusinessDataRequestContext {

    /** 401 の問題の種類の code（{@code auth} の一覧にある）。 */
    static final String AUTHENTICATION_REQUIRED_CODE = "AUTHENTICATION_REQUIRED";

    private final ProblemType authenticationRequired;

    /**
     * 作る。
     *
     * @param registry 問題の種類の一覧
     * @throws IllegalStateException 401 の問題の種類が一覧に無いとき（起動を止める）
     */
    public BusinessDataRequestContext(ProblemTypeRegistry registry) {
        this.authenticationRequired = registry.findByCode(AUTHENTICATION_REQUIRED_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "問題の種類 " + AUTHENTICATION_REQUIRED_CODE + " が一覧にありません（認証の機能の定義が要ります）"));
    }

    /**
     * 要求の利用者 ID を返す。
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
}
