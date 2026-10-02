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
package cherry.mastersmith.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.service.AuthProblemTypeCatalog;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.service.CommonProblemTypeCatalog;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.user.domain.RequestOrigin;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** 利用者の管理の要求の文脈の読み取り（BR2.8）の単体テスト。 */
class UserAdminRequestContextResolverTest {

    private final TraceIdProvider traceIdProvider = mock(TraceIdProvider.class);

    private UserAdminRequestContextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new UserAdminRequestContextResolver(
                new ProblemTypeRegistry(List.of(new CommonProblemTypeCatalog(), new AuthProblemTypeCatalog())),
                traceIdProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void assertAuthenticationRequired() {
        assertThatThrownBy(() -> resolver.currentUserId()).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getProblemType().code()).isEqualTo("AUTHENTICATION_REQUIRED");
            assertThat(e.getProblemType().status()).isEqualTo(401);
        });
    }

    @Test
    @DisplayName("the operator id is read from the standard name of the authentication")
    void readsUserId() {
        authenticate(new UsernamePasswordAuthenticationToken("42", null, List.of()));

        assertThat(resolver.currentUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("no authentication, an unauthenticated token and an anonymous one are AUTHENTICATION_REQUIRED")
    void missingOrAnonymous() {
        assertAuthenticationRequired();

        authenticate(new UsernamePasswordAuthenticationToken("42", null));
        assertAuthenticationRequired();

        authenticate(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertAuthenticationRequired();
    }

    @Test
    @DisplayName("a name that is not a number is AUTHENTICATION_REQUIRED")
    void nonNumericName() {
        authenticate(new UsernamePasswordAuthenticationToken("user@example.com", null, List.of()));

        assertAuthenticationRequired();
    }

    @Test
    @DisplayName("the origin takes the remote address, a User-Agent cut to 512 characters and the current trace id")
    void origin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.71");
        request.addHeader("User-Agent", "u".repeat(513));
        when(traceIdProvider.currentTraceId()).thenReturn(Optional.of("0123456789abcdef0123456789abcdef"));

        RequestOrigin origin = resolver.origin(request);

        assertThat(origin.sourceIp()).isEqualTo("192.0.2.71");
        assertThat(origin.userAgent()).hasSize(512);
        assertThat(origin.traceId()).isEqualTo("0123456789abcdef0123456789abcdef");

        when(traceIdProvider.currentTraceId()).thenReturn(Optional.empty());
        RequestOrigin bare = resolver.origin(new MockHttpServletRequest());
        assertThat(bare.userAgent()).isNull();
        assertThat(bare.traceId()).isNull();
    }

    @Test
    @DisplayName("startup fails when AUTHENTICATION_REQUIRED is not registered")
    void startupFailsWithoutTheProblemType() {
        assertThatThrownBy(() -> new UserAdminRequestContextResolver(
                        new ProblemTypeRegistry(List.of(new CommonProblemTypeCatalog())), traceIdProvider))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTHENTICATION_REQUIRED");
    }
}
