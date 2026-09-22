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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.RefreshToken;
import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.auth.domain.RefreshTokenValues;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class LogoutServiceTest {

    private static final Instant ISSUED = Instant.parse("2026-09-22T00:00:00Z");

    private static final ClientInfo CLIENT = new ClientInfo("192.0.2.1", "UA", null);

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

    private final UserAccountService users = mock(UserAccountService.class);

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final MutableClock clock = new MutableClock(ISSUED.plusSeconds(60));

    private final RefreshTokenValue value = RefreshTokenValues.generate();

    private LogoutService service;

    private RefreshToken token;

    @BeforeEach
    void setUp() {
        service = new LogoutService(repository, users, publisher, clock);
        token = new RefreshToken(7, RefreshTokenValues.hash(value), ISSUED, ISSUED.plus(Duration.ofHours(24)));
        ReflectionTestUtils.setField(token, "tokenId", 100L);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(users.findById(7)).thenReturn(Optional.of(new UserSummary(7, "user@example.com", false)));
    }

    @Test
    @DisplayName("a valid token is revoked and LOGGED_OUT is published with the owner's email and id")
    void revokes() {
        when(repository.revokeIfActive(100L, clock.instant())).thenReturn(1);

        assertThat(service.logout(value, CLIENT)).isTrue();

        ArgumentCaptor<AuthenticationEvent> captor = ArgumentCaptor.forClass(AuthenticationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(AuthenticationEventType.LOGGED_OUT);
        assertThat(captor.getValue().enteredEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("no cookie does nothing and publishes nothing")
    void noCookie() {
        assertThat(service.logout(null, CLIENT)).isFalse();
        verify(repository, never()).findByTokenHash(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("an unknown token does nothing")
    void unknown() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.logout(value, CLIENT)).isFalse();
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("a revoked or expired token does nothing and touches no row")
    void revokedOrExpired() {
        ReflectionTestUtils.setField(token, "revokedAt", ISSUED);
        assertThat(service.logout(value, CLIENT)).isFalse();

        ReflectionTestUtils.setField(token, "revokedAt", null);
        clock.set(ISSUED.plus(Duration.ofHours(24)));
        assertThat(service.logout(value, CLIENT)).isFalse();

        verify(repository, never()).revokeIfActive(anyLong(), any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("losing the conditional revoke publishes nothing")
    void lostRace() {
        when(repository.revokeIfActive(anyLong(), any())).thenReturn(0);

        assertThat(service.logout(value, CLIENT)).isFalse();
        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
