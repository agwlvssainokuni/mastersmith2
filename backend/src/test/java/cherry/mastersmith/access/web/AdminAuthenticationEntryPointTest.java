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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.access.service.AccessDeniedEventPublisher;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import cherry.mastersmith.auth.web.ClientInfoResolver;
import cherry.mastersmith.auth.web.TokenAuthenticationEntryPoint;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;

class AdminAuthenticationEntryPointTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    private final TokenAuthenticationEntryPoint delegate = mock(TokenAuthenticationEntryPoint.class);

    private final AccessDeniedEventPublisher eventPublisher = mock(AccessDeniedEventPublisher.class);

    private final ClientInfoResolver clientInfoResolver = mock(ClientInfoResolver.class);

    private final AdminAuthenticationEntryPoint entryPoint = new AdminAuthenticationEntryPoint(
            delegate, eventPublisher, clientInfoResolver, Clock.fixed(NOW, ZoneOffset.UTC));

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        when(clientInfoResolver.resolve(any()))
                .thenReturn(new ClientInfo("198.51.100.7", "テスト用の利用者環境", "0123456789abcdef0123456789abcdef"));
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private AdminAccessDeniedEvent commenceAndCaptureEvent(String path, AuthenticationException exception)
            throws IOException {
        entryPoint.commence(request(path), response, exception);
        ArgumentCaptor<AdminAccessDeniedEvent> captor = ArgumentCaptor.forClass(AdminAccessDeniedEvent.class);
        verify(eventPublisher, times(1)).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("an admin-only path publishes an event carrying the reason, the path and the client information")
    void adminPathPublishesEvent() throws IOException {
        AdminAccessDeniedEvent event = commenceAndCaptureEvent(
                "/api/admin/check", new TokenAuthenticationException(TokenFailureReason.TOKEN_INVALID));

        assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.TOKEN_INVALID);
        assertThat(event.requestPath()).isEqualTo("/api/admin/check");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.sourceIp()).isEqualTo("198.51.100.7");
        assertThat(event.traceId()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(event.enteredEmail()).isNull();
    }

    @Test
    @DisplayName("an expired token on an admin-only path publishes no event")
    void expiredTokenPublishesNoEvent() throws IOException {
        entryPoint.commence(
                request("/api/admin/check"),
                response,
                new TokenAuthenticationException(TokenFailureReason.TOKEN_EXPIRED));

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("a path that is not admin only publishes no event")
    void otherPathPublishesNoEvent() throws IOException {
        entryPoint.commence(
                request("/api/test-fixture/number"),
                response,
                new TokenAuthenticationException(TokenFailureReason.TOKEN_INVALID));

        verify(eventPublisher, never()).publish(any());
        verifyNoInteractions(clientInfoResolver);
    }

    @Test
    @DisplayName("an exception that is not a token failure is recorded as a missing token")
    void otherExceptionIsMissingToken() throws IOException {
        AdminAccessDeniedEvent event =
                commenceAndCaptureEvent("/api/admin", new InsufficientAuthenticationException("認証が足りない"));

        assertThat(event.failureReason()).isEqualTo(AccessDeniedReason.TOKEN_MISSING);
        assertThat(event.requestPath()).isEqualTo("/api/admin");
    }

    @Test
    @DisplayName("the response body and the debug log are left to the entry point of U2")
    void responseIsDelegated() throws IOException {
        MockHttpServletRequest request = request("/api/admin/check");
        AuthenticationException exception = new TokenAuthenticationException(TokenFailureReason.TOKEN_MALFORMED);

        entryPoint.commence(request, response, exception);

        verify(delegate, times(1)).commence(eq(request), eq(response), eq(exception));
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("the response is still written when a listener of the event fails")
    void responseIsWrittenEvenWhenAListenerFails() throws IOException {
        ApplicationEventPublisher failing = mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("受け取り側の失敗")).when(failing).publishEvent(any(Object.class));
        AdminAuthenticationEntryPoint realPublisherEntryPoint = new AdminAuthenticationEntryPoint(
                delegate,
                new AccessDeniedEventPublisher(failing),
                clientInfoResolver,
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = request("/api/admin/check");
        AuthenticationException exception = new TokenAuthenticationException(TokenFailureReason.TOKEN_INVALID);

        assertThatCode(() -> realPublisherEntryPoint.commence(request, response, exception))
                .doesNotThrowAnyException();

        verify(delegate, times(1)).commence(eq(request), eq(response), eq(exception));
    }

    @Test
    @DisplayName("the event is published before the response is written")
    void eventIsPublishedBeforeTheResponse() throws IOException {
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(eventPublisher, delegate);

        entryPoint.commence(
                request("/api/admin/check"),
                response,
                new TokenAuthenticationException(TokenFailureReason.USER_NOT_FOUND));

        inOrder.verify(eventPublisher).publish(any());
        inOrder.verify(delegate).commence(any(), any(), any());
    }
}
