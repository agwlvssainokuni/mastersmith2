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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.auth.domain.RefreshTokenValues;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TokenRefreshServiceTest {

    private static final Instant ISSUED = Instant.parse("2026-09-22T00:00:00Z");

    private static final UserSummary USER = new UserSummary(7, "user@example.com", true);

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

    private final UserAccountService users = mock(UserAccountService.class);

    private final LoginService loginService = mock(LoginService.class);

    private final MutableClock clock = new MutableClock(ISSUED);

    private final RefreshTokenValue value = RefreshTokenValues.generate();

    private TokenRefreshService service;

    private RefreshToken token;

    @BeforeEach
    void setUp() {
        service = new TokenRefreshService(repository, users, loginService, clock);
        token = new RefreshToken(7, RefreshTokenValues.hash(value), ISSUED, ISSUED.plus(Duration.ofHours(24)));
        ReflectionTestUtils.setField(token, "tokenId", 100L);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(users.findById(7)).thenReturn(Optional.of(USER));
    }

    private void assertFails() {
        assertThatThrownBy(() -> service.refresh(value))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getProblemType()).isEqualTo(AuthProblemTypes.REFRESH_FAILED));
    }

    @Test
    @DisplayName("success revokes the used row and issues new tokens counted from now")
    void success() {
        clock.set(ISSUED.plus(Duration.ofHours(23)));
        when(repository.revokeIfActive(100L, clock.instant())).thenReturn(1);
        IssuedTokens issued = new IssuedTokens(null, RefreshTokenValues.generate(), Duration.ofHours(24), USER);
        when(loginService.issueTokens(USER, clock.instant())).thenReturn(issued);

        assertThat(service.refresh(value)).isSameAs(issued);
        verify(repository).revokeIfActive(100L, clock.instant());
    }

    @Test
    @DisplayName("the token is still usable at 23:59:59 and rejected at exactly 24 hours")
    void expiryBoundary() {
        clock.set(ISSUED.plus(Duration.ofHours(24)).minusSeconds(1));
        when(repository.revokeIfActive(anyLong(), any())).thenReturn(1);
        service.refresh(value);

        clock.set(ISSUED.plus(Duration.ofHours(24)));
        assertFails();
    }

    @Test
    @DisplayName("losing the conditional revoke fails without issuing tokens")
    void lostRace() {
        when(repository.revokeIfActive(anyLong(), any())).thenReturn(0);

        assertFails();
        verify(loginService, never()).issueTokens(any(), any());
    }

    @Test
    @DisplayName("an unknown token fails and touches no row")
    void unknown() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertFails();
        verify(repository, never()).revokeIfActive(anyLong(), any());
    }

    @Test
    @DisplayName("an already used token fails and does not revoke other tokens")
    void used() {
        ReflectionTestUtils.setField(token, "revokedAt", ISSUED);

        assertFails();
        verify(repository, never()).revokeIfActive(anyLong(), any());
    }

    @Test
    @DisplayName("a missing cookie value fails")
    void missing() {
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(BusinessException.class);
    }
}
