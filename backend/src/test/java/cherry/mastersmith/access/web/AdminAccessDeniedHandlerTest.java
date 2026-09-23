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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AccessProblemTypes;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.access.service.AccessDeniedEventPublisher;
import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.web.AuthenticatedUserToken;
import cherry.mastersmith.auth.web.ClientInfoResolver;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminAccessDeniedHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private static final String EMAIL = "member@example.com";

    private final ErrorResponseWriter errorResponseWriter = mock(ErrorResponseWriter.class);

    private final AccessDeniedEventPublisher eventPublisher = mock(AccessDeniedEventPublisher.class);

    private final ClientInfoResolver clientInfoResolver = mock(ClientInfoResolver.class);

    private final AdminAccessDeniedHandler handler = new AdminAccessDeniedHandler(
            errorResponseWriter, eventPublisher, clientInfoResolver, Clock.fixed(NOW, ZoneOffset.UTC));

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        when(clientInfoResolver.resolve(any()))
                .thenReturn(new ClientInfo("198.51.100.7", "テスト用の利用者環境", "0123456789abcdef0123456789abcdef"));
        SecurityContextHolder.getContext()
                .setAuthentication(new AuthenticatedUserToken(new AuthenticatedUser(2L, EMAIL, false)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    @Test
    @DisplayName("the 403 response is written by the error response writer of U1")
    void responseIsWrittenByU1() throws IOException {
        MockHttpServletRequest request = request("/api/admin/check");

        handler.handle(request, response, new AccessDeniedException("Access Denied"));

        verify(errorResponseWriter, times(1)).write(eq(request), eq(response), eq(AccessProblemTypes.ACCESS_DENIED));
        assertThat(AccessProblemTypes.ACCESS_DENIED.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("the event carries the reason NOT_ADMIN and the email address of the logged-in user")
    void eventCarriesNotAdminAndTheEmail() throws IOException {
        handler.handle(request("/api/admin/check"), response, new AccessDeniedException("Access Denied"));

        ArgumentCaptor<AdminAccessDeniedEvent> captor = ArgumentCaptor.forClass(AdminAccessDeniedEvent.class);
        verify(eventPublisher, times(1)).publish(captor.capture());
        AdminAccessDeniedEvent event = captor.getValue();
        assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.NOT_ADMIN);
        assertThat(event.enteredEmail()).isEqualTo(EMAIL);
        assertThat(event.requestPath()).isEqualTo("/api/admin/check");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("without a verified user the email address stays empty")
    void withoutAUserTheEmailIsEmpty() throws IOException {
        SecurityContextHolder.clearContext();

        handler.handle(request("/api/admin/check"), response, new AccessDeniedException("Access Denied"));

        ArgumentCaptor<AdminAccessDeniedEvent> captor = ArgumentCaptor.forClass(AdminAccessDeniedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().enteredEmail()).isNull();
    }

    @Test
    @DisplayName("a denial outside the admin-only area publishes no access denied event")
    void otherPathPublishesNoEvent() throws IOException {
        MockHttpServletRequest request = request("/api/test-fixture/business");

        handler.handle(request, response, new AccessDeniedException("Access Denied"));

        verify(eventPublisher, never()).publish(any());
        verify(errorResponseWriter, times(1)).write(eq(request), eq(response), eq(AccessProblemTypes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("the warning carries the code only, never the email address, the path or a token")
    void warningCarriesTheCodeOnly() throws IOException {
        try (LogEvents events = LogEvents.capture(AdminAccessDeniedHandler.class)) {
            handler.handle(request("/api/admin/check"), response, new AccessDeniedException("Access Denied"));

            assertThat(events.list()).hasSize(1);
            assertThat(events.list().getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(events.list().getFirst().getKeyValuePairs())
                    .singleElement()
                    .satisfies(pair -> {
                        assertThat(pair.key).isEqualTo("code");
                        assertThat(pair.value).isEqualTo("ACCESS_DENIED");
                    });
            assertThat(events.list().getFirst().getFormattedMessage())
                    .doesNotContain(EMAIL)
                    .doesNotContain("/api/admin")
                    .doesNotContain("Bearer");
        }
    }

    @Test
    @DisplayName("a failing listener does not change the response")
    void listenerFailureDoesNotChangeTheResponse() throws IOException {
        ApplicationEventPublisher failing = mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("受け取り側の失敗")).when(failing).publishEvent(any(Object.class));
        AdminAccessDeniedHandler realPublisherHandler = new AdminAccessDeniedHandler(
                errorResponseWriter,
                new AccessDeniedEventPublisher(failing),
                clientInfoResolver,
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = request("/api/admin/check");

        assertThatCode(() -> realPublisherHandler.handle(request, response, new AccessDeniedException("Access Denied")))
                .doesNotThrowAnyException();

        verify(errorResponseWriter, times(1)).write(eq(request), eq(response), eq(AccessProblemTypes.ACCESS_DENIED));
    }
}
