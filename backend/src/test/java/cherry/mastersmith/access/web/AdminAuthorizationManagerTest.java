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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.auth.web.AuthenticatedUserToken;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class AdminAuthorizationManagerTest {

    private final AdminAuthorizationManager manager = new AdminAuthorizationManager();

    private final RequestAuthorizationContext context =
            new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/admin/check"));

    private boolean granted(Authentication authentication) {
        Supplier<Authentication> supplier = () -> authentication;
        AuthorizationResult result = manager.authorize(supplier, context);
        assertThat(result).isNotNull();
        return result.isGranted();
    }

    @Test
    @DisplayName("an administrator is granted")
    void administratorIsGranted() {
        assertThat(granted(new AuthenticatedUserToken(new AuthenticatedUser(1L, "admin@example.com", true))))
                .isTrue();
    }

    @Test
    @DisplayName("a logged-in user without the administrator flag is denied")
    void nonAdministratorIsDenied() {
        assertThat(granted(new AuthenticatedUserToken(new AuthenticatedUser(2L, "member@example.com", false))))
                .isFalse();
    }

    @Test
    @DisplayName("a request without any authentication is denied")
    void noAuthenticationIsDenied() {
        assertThat(granted(null)).isFalse();
    }

    @Test
    @DisplayName("an anonymous authentication is denied")
    void anonymousIsDenied() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(granted(anonymous)).isFalse();
    }

    @Test
    @DisplayName("a principal that is not a verified user is denied even with an admin-looking role")
    void otherPrincipalIsDenied() {
        Authentication other = UsernamePasswordAuthenticationToken.authenticated(
                "admin@example.com", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

        assertThat(granted(other)).isFalse();
    }

    @Test
    @DisplayName("an authentication that is not authenticated is denied")
    void notAuthenticatedIsDenied() {
        Authentication unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(
                new AuthenticatedUser(3L, "a@example.com", true), null);

        assertThat(granted(unauthenticated)).isFalse();
    }

    @Test
    @DisplayName("the decision uses only the principal so that no database access is needed")
    void decisionUsesOnlyThePrincipal() {
        AuthenticatedUserToken token = new AuthenticatedUserToken(new AuthenticatedUser(4L, "admin@example.com", true));

        assertThat(token.getAuthorities()).isEqualTo(List.of());
        assertThat(granted(token)).isTrue();
    }
}
