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
package cherry.mastersmith.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AccessDeniedEventPublisherTest {

    private static final String SECRET_MESSAGE = "受け取り側の内部の事情";

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final AccessDeniedEventPublisher publisher = new AccessDeniedEventPublisher(eventPublisher);

    private static AdminAccessDeniedEvent event() {
        return AdminAccessDeniedEvent.of(
                Instant.parse("2026-09-22T00:00:00Z"),
                AccessDeniedReason.NOT_ADMIN,
                "member@example.com",
                "/api/admin/check",
                new ClientInfo("198.51.100.7", "agent", "0123456789abcdef0123456789abcdef"));
    }

    @Test
    @DisplayName("the event is published exactly once")
    void publishesOnce() {
        AdminAccessDeniedEvent event = event();

        publisher.publish(event);

        verify(eventPublisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("a failing listener does not propagate to the caller")
    void listenerFailureIsNotPropagated() {
        doThrow(new IllegalStateException(SECRET_MESSAGE)).when(eventPublisher).publishEvent(any(Object.class));

        assertThatCode(() -> publisher.publish(event())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a failing listener is reported once as a warning with the exception type only")
    void listenerFailureIsWarnedOnce() {
        doThrow(new IllegalStateException(SECRET_MESSAGE)).when(eventPublisher).publishEvent(any(Object.class));

        try (LogEvents events = LogEvents.capture(AccessDeniedEventPublisher.class)) {
            publisher.publish(event());

            assertThat(events.list()).hasSize(1);
            assertThat(events.list().getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(events.list().getFirst().getKeyValuePairs().toString())
                    .contains("exceptionType")
                    .contains(IllegalStateException.class.getName());
        }
    }

    @Test
    @DisplayName("the warning carries neither the exception message nor a stack trace")
    void warningHidesTheExceptionMessage() {
        doThrow(new IllegalStateException(SECRET_MESSAGE)).when(eventPublisher).publishEvent(any(Object.class));

        try (LogEvents events = LogEvents.capture(AccessDeniedEventPublisher.class)) {
            publisher.publish(event());

            assertThat(events.list().getFirst().getFormattedMessage()).doesNotContain(SECRET_MESSAGE);
            assertThat(events.list().getFirst().getThrowableProxy()).isNull();
        }
    }

    @Test
    @DisplayName("a successful publication writes no warning")
    void successWritesNoWarning() {
        try (LogEvents events = LogEvents.capture(AccessDeniedEventPublisher.class)) {
            publisher.publish(event());

            assertThat(events.list()).isEmpty();
        }
    }
}
