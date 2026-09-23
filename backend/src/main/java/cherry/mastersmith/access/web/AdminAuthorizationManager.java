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

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * 管理者のみの API の判定（BR2.2〜BR2.4、NFR3.5）。
 *
 * <p>U2 の {@code AuthenticatedUserToken} は権限の一覧を持たないため、役割の文字列ではなく主体の値で判断する。主体は、U2 が
 * 要求ごとに内部DBから読んだ {@link AuthenticatedUser} である（画面から送られた値・トークンの中身は使わない）。U3 は DB を
 * 読まない（NFR1.2）。
 *
 * <p>後続 Intent F で役割・権限の判定を足す場所はここになる（ADR-003）。
 */
public class AdminAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        boolean granted = current != null
                && current.isAuthenticated()
                && current.getPrincipal() instanceof AuthenticatedUser user
                && user.admin();
        return new AuthorizationDecision(granted);
    }
}
