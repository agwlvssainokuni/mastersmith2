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

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** 検証済みのアクセストークンの認証の結果。主体は {@link AuthenticatedUser}（U3 が使う）。資格情報は持たない。 */
public class AuthenticatedUserToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final AuthenticatedUser user;

    /**
     * 作る。
     *
     * @param user 検証済みの利用者
     */
    public AuthenticatedUserToken(AuthenticatedUser user) {
        super(List.of());
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return Long.toString(user.userId());
    }
}
