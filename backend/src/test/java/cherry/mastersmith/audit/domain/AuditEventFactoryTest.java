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
package cherry.mastersmith.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AccessEventType;
import cherry.mastersmith.access.domain.AccessResult;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.LoginFailureReason;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** 出来事から監査イベントへの写し取り（BR1.1、BR1.2、BR1.5、BR1.6、BR2.1）のテスト。 */
class AuditEventFactoryTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-22T01:02:03Z");

    private static final ClientInfo CLIENT = new ClientInfo("192.0.2.10", "Mozilla/5.0", "trace-0001");

    private static AuthenticationEvent authenticationEvent(
            AuthenticationEventType eventType, LoginFailureReason reason, String email) {
        return AuthenticationEvent.of(eventType, OCCURRED_AT, email, 42L, reason, CLIENT);
    }

    private static AdminAccessDeniedEvent accessDeniedEvent(
            AccessDeniedReason reason, String email, String requestPath) {
        return AdminAccessDeniedEvent.of(OCCURRED_AT, reason, email, requestPath, CLIENT);
    }

    @ParameterizedTest
    @CsvSource({
        "LOGIN_SUCCEEDED,LOGIN_SUCCEEDED,SUCCESS",
        "LOGIN_FAILED,LOGIN_FAILED,FAILURE",
        "LOGGED_OUT,LOGGED_OUT,SUCCESS"
    })
    @DisplayName("each authentication event type maps to its audit type and result")
    void authenticationTypesAndResults(
            AuthenticationEventType source, AuditEventType expectedType, AuditResult expectedResult) {
        AuditEvent audit = AuditEventFactory.from(authenticationEvent(source, null, "user@example.com"));

        assertThat(audit.getEventType()).isEqualTo(expectedType);
        assertThat(audit.getResult()).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("an access denial maps to ACCESS_DENIED with a FAILURE result")
    void accessDenialTypeAndResult() {
        AuditEvent audit = AuditEventFactory.from(
                accessDeniedEvent(AccessDeniedReason.NOT_ADMIN, "member@example.com", "/api/admin/check"));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.ACCESS_DENIED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.FAILURE);
    }

    @ParameterizedTest
    @EnumSource(LoginFailureReason.class)
    @DisplayName("every login failure reason is copied into the audit event")
    void everyLoginFailureReasonIsCopied(LoginFailureReason reason) {
        AuditEvent audit = AuditEventFactory.from(
                authenticationEvent(AuthenticationEventType.LOGIN_FAILED, reason, "user@example.com"));

        assertThat(audit.getFailureReason()).isNotNull();
        assertThat(audit.getFailureReason().name()).isEqualTo(reason.name());
    }

    @ParameterizedTest
    @EnumSource(AccessDeniedReason.class)
    @DisplayName("every access denied reason is copied into the audit event")
    void everyAccessDeniedReasonIsCopied(AccessDeniedReason reason) {
        AuditEvent audit = AuditEventFactory.from(accessDeniedEvent(reason, null, "/api/admin/check"));

        assertThat(audit.getFailureReason()).isNotNull();
        assertThat(audit.getFailureReason().name()).isEqualTo(reason.name());
    }

    @Test
    @DisplayName("the audit event keeps the time of the event and drops the user id")
    void occurredAtAndNoUserId() {
        AuditEvent audit = AuditEventFactory.from(
                authenticationEvent(AuthenticationEventType.LOGIN_SUCCEEDED, null, "user@example.com"));

        assertThat(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(audit.getSourceIp()).isEqualTo("192.0.2.10");
        assertThat(audit.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(audit.getTraceId()).isEqualTo("trace-0001");
        assertThat(audit.getFailureReason()).isNull();
        // 利用者IDは記録項目に含まれないため、監査イベントに項目そのものが無い。
        assertThat(audit.toString()).doesNotContain("42");
    }

    @Test
    @DisplayName("an unknown email address is recorded as entered")
    void unknownEmailIsRecordedAsEntered() {
        String unknown = "nobody-あ@example.com";

        AuditEvent audit = AuditEventFactory.from(
                authenticationEvent(AuthenticationEventType.LOGIN_FAILED, LoginFailureReason.USER_NOT_FOUND, unknown));

        assertThat(audit.getEnteredEmail()).isEqualTo(unknown);
    }

    @Test
    @DisplayName("only an access denial records the request path, without its query part")
    void requestPathOnlyForAccessDenial() {
        AuditEvent denied = AuditEventFactory.from(
                accessDeniedEvent(AccessDeniedReason.NOT_ADMIN, "member@example.com", "/api/admin/check?token=secret"));
        AuditEvent login = AuditEventFactory.from(
                authenticationEvent(AuthenticationEventType.LOGIN_SUCCEEDED, null, "user@example.com"));

        assertThat(denied.getRequestPath()).isEqualTo("/api/admin/check");
        assertThat(login.getRequestPath()).isNull();
    }

    @Test
    @DisplayName("long values are truncated and the masked email of the source event is not copied")
    void longValuesAreTruncatedAndEmailIsTakenFromTheAccessor() {
        String longEmail = "あ".repeat(300) + "@example.com";
        String longUserAgent = "u".repeat(700);
        String longPath = "/api/admin/" + "p".repeat(700);
        AdminAccessDeniedEvent event = new AdminAccessDeniedEvent(
                AccessEventType.ACCESS_DENIED,
                OCCURRED_AT,
                AccessResult.FAILURE,
                AccessDeniedReason.NOT_ADMIN,
                longEmail,
                "192.0.2.10",
                longUserAgent,
                longPath,
                "trace-0001");

        AuditEvent audit = AuditEventFactory.from(event);

        assertThat(audit.getEnteredEmail())
                .hasSize(AuditText.MAX_ENTERED_EMAIL_LENGTH)
                .isEqualTo(longEmail.substring(0, AuditText.MAX_ENTERED_EMAIL_LENGTH));
        assertThat(audit.getUserAgent()).hasSize(AuditText.MAX_USER_AGENT_LENGTH);
        assertThat(audit.getRequestPath()).hasSize(AuditText.MAX_REQUEST_PATH_LENGTH);
        // 出来事の文字列化は伏せ字だが、写し取りは値そのものを取る。
        assertThat(event.toString()).doesNotContain(longEmail.substring(0, 10));
        assertThat(audit.getEnteredEmail()).startsWith("あ");
    }
}
