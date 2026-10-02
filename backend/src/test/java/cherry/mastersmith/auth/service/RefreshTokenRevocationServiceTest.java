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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.auth.testsupport.MutableClock;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RefreshTokenRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

    private final MutableClock clock = new MutableClock(NOW);

    private final Logger logger = (Logger) LoggerFactory.getLogger(RefreshTokenRevocationService.class);

    private Level levelBefore;

    private RefreshTokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenRevocationService(repository, clock);
        levelBefore = logger.getLevel();
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void restore() {
        logger.setLevel(levelBefore);
    }

    @Test
    @DisplayName("tokens are revoked at the time of the injected clock")
    void usesInjectedClock() {
        clock.set(NOW.plus(Duration.ofMinutes(7)));
        when(repository.revokeAllActiveByUserId(7L, NOW.plus(Duration.ofMinutes(7))))
                .thenReturn(2);

        service.revokeAllRefreshTokens(7);

        verify(repository, times(1)).revokeAllActiveByUserId(7L, NOW.plus(Duration.ofMinutes(7)));
    }

    @Test
    @DisplayName("the number of revoked rows is returned as the result")
    void returnsCount() {
        when(repository.revokeAllActiveByUserId(7L, NOW)).thenReturn(100);

        assertThat(service.revokeAllRefreshTokens(7)).isEqualTo(new RevokeAllResult(100));
    }

    @Test
    @DisplayName("no active row is still a success with zero")
    void zeroIsSuccess() {
        when(repository.revokeAllActiveByUserId(8L, NOW)).thenReturn(0);

        assertThat(service.revokeAllRefreshTokens(8).revoked()).isZero();
    }

    @Test
    @DisplayName("the debug log carries only the user id and the count, never a token value or hash")
    void logsOnlyUserIdAndCount() {
        when(repository.revokeAllActiveByUserId(7L, NOW)).thenReturn(3);

        try (LogEvents events = LogEvents.capture(RefreshTokenRevocationService.class)) {
            service.revokeAllRefreshTokens(7);

            List<ILoggingEvent> logged = events.list();
            assertThat(logged).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key)
                        .containsExactly("userId", "revoked");
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.value)
                        .containsExactly(7L, 3);
                assertThat(event.getFormattedMessage()).doesNotContainIgnoringCase("hash");
            });
        }
    }

    @Test
    @DisplayName("nothing is logged above debug so that the operation stays quiet at the default level")
    void notLoggedAtInfo() {
        logger.setLevel(Level.INFO);
        when(repository.revokeAllActiveByUserId(7L, NOW)).thenReturn(1);

        try (LogEvents events = LogEvents.capture(RefreshTokenRevocationService.class)) {
            service.revokeAllRefreshTokens(7);

            assertThat(events.list()).isEmpty();
        }
    }

    @Test
    @DisplayName("the result rejects a negative count and accepts zero")
    void resultRejectsNegative() {
        assertThatThrownBy(() -> new RevokeAllResult(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RevokeAllResult(0).revoked()).isZero();
    }
}
