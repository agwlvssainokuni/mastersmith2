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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.domain.AccessTokenValue;
import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import cherry.mastersmith.auth.service.AccessTokenService;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

class AccessTokenAuthenticationProviderTest {

    private static final String TOKEN = "header.payload.signature";

    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);

    private final UserAccountService userAccountService = mock(UserAccountService.class);

    private final AccessTokenAuthenticationProvider provider =
            new AccessTokenAuthenticationProvider(accessTokenService, userAccountService);

    @BeforeEach
    void setUp() {
        when(accessTokenService.verify(new AccessTokenValue(TOKEN))).thenReturn(7L);
    }

    private static UserSummary user(boolean admin, boolean suspended) {
        return new UserSummary(7, "user@example.com", admin, "テスト 利用者", "ja", "system", "md", suspended);
    }

    @Test
    @DisplayName("an active user becomes the authenticated principal with the admin flag from the database")
    void activeUser() {
        when(userAccountService.findById(7)).thenReturn(Optional.of(user(true, false)));

        Authentication result = provider.authenticate(new BearerTokenAuthenticationToken(TOKEN));

        assertThat(result).isInstanceOf(AuthenticatedUserToken.class);
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isEqualTo(new AuthenticatedUser(7, "user@example.com", true));
    }

    @Test
    @DisplayName("a suspended user is rejected with the USER_SUSPENDED reason")
    void suspendedUser() {
        when(userAccountService.findById(7)).thenReturn(Optional.of(user(false, true)));

        assertThatThrownBy(() -> provider.authenticate(new BearerTokenAuthenticationToken(TOKEN)))
                .isInstanceOfSatisfying(
                        TokenAuthenticationException.class,
                        e -> assertThat(e.reason()).isEqualTo(TokenFailureReason.USER_SUSPENDED));
    }

    @Test
    @DisplayName("a suspended administrator is rejected as well")
    void suspendedAdministrator() {
        when(userAccountService.findById(7)).thenReturn(Optional.of(user(true, true)));

        assertThatThrownBy(() -> provider.authenticate(new BearerTokenAuthenticationToken(TOKEN)))
                .isInstanceOfSatisfying(
                        TokenAuthenticationException.class,
                        e -> assertThat(e.reason()).isEqualTo(TokenFailureReason.USER_SUSPENDED));
    }

    @Test
    @DisplayName("a missing user is rejected with the USER_NOT_FOUND reason")
    void missingUser() {
        when(userAccountService.findById(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(new BearerTokenAuthenticationToken(TOKEN)))
                .isInstanceOfSatisfying(
                        TokenAuthenticationException.class,
                        e -> assertThat(e.reason()).isEqualTo(TokenFailureReason.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("an invalid token is rejected before the user is read")
    void invalidToken() {
        when(accessTokenService.verify(any()))
                .thenThrow(new TokenAuthenticationException(TokenFailureReason.TOKEN_EXPIRED));

        assertThatThrownBy(() -> provider.authenticate(new BearerTokenAuthenticationToken(TOKEN)))
                .isInstanceOfSatisfying(
                        TokenAuthenticationException.class,
                        e -> assertThat(e.reason()).isEqualTo(TokenFailureReason.TOKEN_EXPIRED));
        verify(userAccountService, never()).findById(anyLong());
    }

    @Test
    @DisplayName("the suspension check reads the user only once")
    void readsTheUserOnce() {
        when(userAccountService.findById(7)).thenReturn(Optional.of(user(false, true)));

        assertThatThrownBy(() -> provider.authenticate(new BearerTokenAuthenticationToken(TOKEN)))
                .isInstanceOf(TokenAuthenticationException.class);
        verify(userAccountService, times(1)).findById(7);
        verify(userAccountService, never()).isSuspended(anyLong());
    }

    @Test
    @DisplayName("only bearer tokens are supported")
    void supportsOnlyBearer() {
        assertThat(provider.supports(BearerTokenAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }
}
