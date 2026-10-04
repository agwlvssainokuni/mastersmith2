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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.user.domain.InitialAdminRescueCondition;
import cherry.mastersmith.user.domain.InitialAdminRescuedEvent;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 初期管理者の救済の知らせの受け手（Intent 261004-safety-carryover の FR1.2(c)(e)・FR1.2a）の単体テスト。 */
class InitialAdminRescueListenerTest {

    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");

    private final LoginAttemptStateRepository loginAttemptStates = mock(LoginAttemptStateRepository.class);

    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);

    private InitialAdminRescueListener listener() {
        return new InitialAdminRescueListener(loginAttemptStates, refreshTokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InitialAdminRescuedEvent event(long userId) {
        return new InitialAdminRescuedEvent(userId, Set.of(InitialAdminRescueCondition.SUSPENDED), NOW);
    }

    @Test
    @DisplayName("the rescue resets the failures, unlocks and revokes every active refresh token of the user")
    void resetsAndRevokes() {
        when(refreshTokens.revokeAllActiveByUserId(31L, NOW)).thenReturn(2);

        listener().onInitialAdminRescued(event(31L));

        InOrder order = inOrder(loginAttemptStates, refreshTokens);
        // ロックの状態の行が無い利用者でも、行を作ってから 0 にする。
        order.verify(loginAttemptStates).createIfAbsent(31L);
        order.verify(loginAttemptStates).update(31L, 0, null);
        order.verify(refreshTokens).revokeAllActiveByUserId(31L, NOW);
        verifyNoMoreInteractions(loginAttemptStates, refreshTokens);
    }

    @Test
    @DisplayName("no active refresh token is not an error and the debug log carries only the user id and the count")
    void noActiveToken() {
        when(refreshTokens.revokeAllActiveByUserId(anyLong(), any())).thenReturn(0);

        Logger logger = (Logger) LoggerFactory.getLogger(InitialAdminRescueListener.class);
        Level original = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try (LogEvents logs = LogEvents.capture(InitialAdminRescueListener.class)) {
            listener().onInitialAdminRescued(event(32L));
            List<ILoggingEvent> events = logs.list();
            assertThat(events).singleElement().satisfies(logged -> {
                assertThat(logged.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(logged.getKeyValuePairs())
                        .extracting(pair -> pair.key + "=" + pair.value)
                        .containsExactly("userId=32", "revoked=0");
            });
        } finally {
            logger.setLevel(original);
        }
    }

    @Test
    @DisplayName("the listener joins the rescue transaction and runs before the lowest-precedence listeners")
    void joinsTheRescueTransaction() throws NoSuchMethodException {
        Method method =
                InitialAdminRescueListener.class.getMethod("onInitialAdminRescued", InitialAdminRescuedEvent.class);

        assertThat(method.getAnnotation(EventListener.class)).isNotNull();
        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(method.getAnnotation(Order.class).value()).isZero();
    }
}
