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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AccessEventType;
import cherry.mastersmith.access.domain.AccessResult;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.auth.domain.LoginFailureReason;
import cherry.mastersmith.invitation.domain.InvitationCancelledEvent;
import cherry.mastersmith.invitation.domain.InvitationIssuedEvent;
import cherry.mastersmith.invitation.domain.InvitationResentEvent;
import cherry.mastersmith.invitation.domain.LinkRejection;
import cherry.mastersmith.invitation.domain.RegistrationCompletedEvent;
import cherry.mastersmith.invitation.domain.RegistrationFailedEvent;
import cherry.mastersmith.user.domain.InitialAdminCreatedEvent;
import cherry.mastersmith.user.domain.InitialAdminRescueCondition;
import cherry.mastersmith.user.domain.InitialAdminRescuedEvent;
import cherry.mastersmith.user.domain.PasswordChangeFailureReason;
import cherry.mastersmith.user.domain.PasswordChangedEvent;
import cherry.mastersmith.user.domain.RequestOrigin;
import cherry.mastersmith.useradmin.domain.AdminOperation;
import cherry.mastersmith.useradmin.domain.UserAdminAuditEvent;
import cherry.mastersmith.useradmin.domain.UserAdminAuditFailure;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

    @Test
    @DisplayName("a suspended login maps to a LOGIN_FAILED row with ACCOUNT_SUSPENDED and the entered email")
    void suspendedLogin() {
        AuditEvent audit = AuditEventFactory.from(authenticationEvent(
                AuthenticationEventType.LOGIN_FAILED, LoginFailureReason.ACCOUNT_SUSPENDED, "suspended@example.com"));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(audit.getFailureReason()).isEqualTo(AuditFailureReason.ACCOUNT_SUSPENDED);
        // 既存のログインの失敗と同じく、入れたメールアドレスを記録し、利用者IDは記録しない（写し取りは変えない）。
        assertThat(audit.getEnteredEmail()).isEqualTo("suspended@example.com");
        assertThat(audit.getActorUserId()).isNull();
        assertThat(audit.getTargetUserId()).isNull();
        assertThat(audit.getSourceIp()).isEqualTo("192.0.2.10");
        assertThat(audit.getTraceId()).isEqualTo("trace-0001");
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

    @Test
    @DisplayName("a password change success records PASSWORD_CHANGED with the user as both actor and target")
    void passwordChangeSuccess() {
        AuditEvent audit = AuditEventFactory.from(PasswordChangedEvent.succeeded(
                21, OCCURRED_AT, new RequestOrigin("192.0.2.30", "Mozilla/5.0", "trace-0030")));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.PASSWORD_CHANGED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(audit.getFailureReason()).isNull();
        assertThat(audit.getActorUserId()).isEqualTo(21L);
        assertThat(audit.getTargetUserId()).isEqualTo(21L);
        assertThat(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(audit.getSourceIp()).isEqualTo("192.0.2.30");
        assertThat(audit.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(audit.getTraceId()).isEqualTo("trace-0030");
        assertThat(audit.getEnteredEmail()).isNull();
        assertThat(audit.getRequestPath()).isNull();
        assertThat(audit.getTargetInvitationId()).isNull();
        assertThat(audit.getDslHash()).isNull();
        assertThat(audit.getDslSource()).isNull();
        assertThat(audit.getRejectionKind()).isNull();
    }

    @Test
    @DisplayName("a current-password mismatch records PASSWORD_CHANGED FAILURE with CURRENT_PASSWORD_MISMATCH")
    void passwordChangeFailure() {
        AuditEvent audit = AuditEventFactory.from(PasswordChangedEvent.failed(
                21,
                PasswordChangeFailureReason.CURRENT_PASSWORD_MISMATCH,
                OCCURRED_AT,
                new RequestOrigin("192.0.2.30", null, null)));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.PASSWORD_CHANGED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(audit.getFailureReason()).isEqualTo(AuditFailureReason.CURRENT_PASSWORD_MISMATCH);
        assertThat(audit.getActorUserId()).isEqualTo(21L);
        assertThat(audit.getTargetUserId()).isEqualTo(21L);
        assertThat(audit.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("the existing kinds of events leave both target columns empty")
    void existingEventsHaveNoTarget() {
        AuditEvent login = AuditEventFactory.from(
                authenticationEvent(AuthenticationEventType.LOGIN_SUCCEEDED, null, "user@example.com"));
        AuditEvent denied = AuditEventFactory.from(
                accessDeniedEvent(AccessDeniedReason.NOT_ADMIN, "member@example.com", "/api/admin/check"));

        assertThat(login.getTargetUserId()).isNull();
        assertThat(login.getTargetInvitationId()).isNull();
        assertThat(denied.getTargetUserId()).isNull();
        assertThat(denied.getTargetInvitationId()).isNull();
    }

    @Test
    @DisplayName("the result of PASSWORD_CHANGED cannot be derived from the type alone")
    void passwordChangedResultComesFromTheEvent() {
        assertThatThrownBy(() -> AuditEventFactory.resultOf(AuditEventType.PASSWORD_CHANGED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a long User-Agent of a password change is truncated to 512 code points")
    void passwordChangeUserAgentIsTruncated() {
        AuditEvent audit = AuditEventFactory.from(PasswordChangedEvent.succeeded(
                21, OCCURRED_AT, new RequestOrigin("192.0.2.30", "😀".repeat(400), null)));

        assertThat(audit.getUserAgent().codePointCount(0, audit.getUserAgent().length()))
                .isLessThanOrEqualTo(AuditText.MAX_USER_AGENT_LENGTH);
    }

    private static final RequestOrigin INVITE_ORIGIN = new RequestOrigin("192.0.2.40", "Agent/1", "trace-0040");

    @Test
    @DisplayName("invitation admin events record the admin as actor and the invitation as target with SUCCESS")
    void invitationAdminEvents() {
        AuditEvent issued = AuditEventFactory.from(InvitationIssuedEvent.of(5, 9, OCCURRED_AT, INVITE_ORIGIN));
        AuditEvent resent = AuditEventFactory.from(InvitationResentEvent.of(5, 9, OCCURRED_AT, INVITE_ORIGIN));
        AuditEvent cancelled = AuditEventFactory.from(InvitationCancelledEvent.of(5, 9, OCCURRED_AT, INVITE_ORIGIN));

        assertThat(List.of(issued, resent, cancelled))
                .extracting(AuditEvent::getEventType)
                .containsExactly(
                        AuditEventType.INVITATION_ISSUED,
                        AuditEventType.INVITATION_RESENT,
                        AuditEventType.INVITATION_CANCELLED);
        for (AuditEvent audit : List.of(issued, resent, cancelled)) {
            assertThat(audit.getResult()).isEqualTo(AuditResult.SUCCESS);
            assertThat(audit.getActorUserId()).isEqualTo(9L);
            assertThat(audit.getTargetInvitationId()).isEqualTo(5L);
            assertThat(audit.getTargetUserId()).isNull();
            assertThat(audit.getFailureReason()).isNull();
            assertThat(audit.getEnteredEmail()).isNull();
            assertThat(audit.getSourceIp()).isEqualTo("192.0.2.40");
            assertThat(audit.getUserAgent()).isEqualTo("Agent/1");
            assertThat(audit.getTraceId()).isEqualTo("trace-0040");
            assertThat(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
        }
    }

    @Test
    @DisplayName("a completed registration records no actor, the invitation and the created user")
    void registrationCompleted() {
        AuditEvent audit = AuditEventFactory.from(RegistrationCompletedEvent.of(5, 31, OCCURRED_AT, INVITE_ORIGIN));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.REGISTRATION_COMPLETED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(audit.getActorUserId()).isNull();
        assertThat(audit.getTargetInvitationId()).isEqualTo(5L);
        assertThat(audit.getTargetUserId()).isEqualTo(31L);
    }

    @ParameterizedTest
    @CsvSource({
        "INVITATION_EXPIRED, INVITATION_EXPIRED",
        "INVITATION_ALREADY_USED, INVITATION_ALREADY_USED",
        "INVITATION_CANCELLED, INVITATION_CANCELLED",
        "EMAIL_ALREADY_REGISTERED, EMAIL_ALREADY_REGISTERED"
    })
    @DisplayName("a failed registration records FAILURE, the reason and the found invitation")
    void registrationFailed(LinkRejection reason, AuditFailureReason expected) {
        AuditEvent audit = AuditEventFactory.from(RegistrationFailedEvent.of(5L, reason, OCCURRED_AT, INVITE_ORIGIN));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.REGISTRATION_FAILED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(audit.getFailureReason()).isEqualTo(expected);
        assertThat(audit.getTargetInvitationId()).isEqualTo(5L);
        assertThat(audit.getActorUserId()).isNull();
        assertThat(audit.getTargetUserId()).isNull();
    }

    @Test
    @DisplayName("a failed registration for an unknown token has no target invitation")
    void registrationFailedNotFound() {
        AuditEvent audit = AuditEventFactory.from(
                RegistrationFailedEvent.of(null, LinkRejection.INVITATION_NOT_FOUND, OCCURRED_AT, INVITE_ORIGIN));

        assertThat(audit.getFailureReason()).isEqualTo(AuditFailureReason.INVITATION_NOT_FOUND);
        assertThat(audit.getTargetInvitationId()).isNull();
    }

    @Test
    @DisplayName("the result of the invitation types is decided by the type")
    void invitationTypeResults() {
        assertThat(AuditEventFactory.resultOf(AuditEventType.INVITATION_ISSUED)).isEqualTo(AuditResult.SUCCESS);
        assertThat(AuditEventFactory.resultOf(AuditEventType.INVITATION_RESENT)).isEqualTo(AuditResult.SUCCESS);
        assertThat(AuditEventFactory.resultOf(AuditEventType.INVITATION_CANCELLED))
                .isEqualTo(AuditResult.SUCCESS);
        assertThat(AuditEventFactory.resultOf(AuditEventType.REGISTRATION_COMPLETED))
                .isEqualTo(AuditResult.SUCCESS);
        assertThat(AuditEventFactory.resultOf(AuditEventType.REGISTRATION_FAILED))
                .isEqualTo(AuditResult.FAILURE);
    }

    @Test
    @DisplayName("a long user agent of an invitation event is truncated")
    void invitationUserAgentIsTruncated() {
        AuditEvent audit = AuditEventFactory.from(
                InvitationIssuedEvent.of(5, 9, OCCURRED_AT, new RequestOrigin("192.0.2.40", "a".repeat(512), null)));

        assertThat(audit.getUserAgent()).hasSizeLessThanOrEqualTo(512);
    }

    private static final RequestOrigin ADMIN_ORIGIN = new RequestOrigin("192.0.2.50", "Agent/50", "trace-0050");

    @ParameterizedTest
    @CsvSource({
        "GRANT_ADMIN,USER_ADMIN_GRANTED",
        "REVOKE_ADMIN,USER_ADMIN_REVOKED",
        "SUSPEND,USER_SUSPENDED",
        "RESUME,USER_RESUMED",
        "RESET_LOGIN_FAILURES,LOGIN_FAILURES_RESET"
    })
    @DisplayName("a successful user admin operation records its type, SUCCESS, the actor and the target")
    void userAdminSuccess(AdminOperation operation, AuditEventType expected) {
        AuditEvent audit =
                AuditEventFactory.from(UserAdminAuditEvent.succeeded(operation, 3, 77, OCCURRED_AT, ADMIN_ORIGIN));

        assertThat(audit.getEventType()).isEqualTo(expected);
        assertThat(audit.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(audit.getFailureReason()).isNull();
        assertThat(audit.getActorUserId()).isEqualTo(3L);
        assertThat(audit.getTargetUserId()).isEqualTo(77L);
        assertThat(audit.getTargetInvitationId()).isNull();
        assertThat(audit.getEnteredEmail()).isNull();
        assertThat(audit.getRequestPath()).isNull();
        assertThat(audit.getDslHash()).isNull();
        assertThat(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(audit.getSourceIp()).isEqualTo("192.0.2.50");
        assertThat(audit.getUserAgent()).isEqualTo("Agent/50");
        assertThat(audit.getTraceId()).isEqualTo("trace-0050");
    }

    @ParameterizedTest
    @CsvSource({
        "USER_NOT_FOUND,USER_NOT_FOUND",
        "SELF_OPERATION,SELF_OPERATION",
        "TARGET_SUSPENDED,TARGET_SUSPENDED",
        "NO_CHANGE,NO_CHANGE",
        "LAST_ACTIVE_ADMIN,LAST_ACTIVE_ADMIN",
        "NOT_ADMIN,NOT_ADMIN"
    })
    @DisplayName("a rejected user admin operation records FAILURE and the mapped reason")
    void userAdminFailure(UserAdminAuditFailure failure, AuditFailureReason expected) {
        AuditEvent audit = AuditEventFactory.from(
                UserAdminAuditEvent.failed(AdminOperation.REVOKE_ADMIN, 3, 77, failure, OCCURRED_AT, ADMIN_ORIGIN));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.USER_ADMIN_REVOKED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(audit.getFailureReason()).isEqualTo(expected);
        assertThat(audit.getEnteredEmail()).isNull();
    }

    @Test
    @DisplayName("the target is the requested user id even when no such user exists")
    void userAdminMissingTarget() {
        AuditEvent audit = AuditEventFactory.from(UserAdminAuditEvent.failed(
                AdminOperation.RESET_LOGIN_FAILURES,
                3,
                987_654,
                UserAdminAuditFailure.USER_NOT_FOUND,
                OCCURRED_AT,
                ADMIN_ORIGIN));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILURES_RESET);
        assertThat(audit.getTargetUserId()).isEqualTo(987_654L);
        assertThat(audit.getFailureReason()).isEqualTo(AuditFailureReason.USER_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuditEventType.class,
            names = {
                "USER_ADMIN_GRANTED",
                "USER_ADMIN_REVOKED",
                "USER_SUSPENDED",
                "USER_RESUMED",
                "LOGIN_FAILURES_RESET"
            })
    @DisplayName("the result of the user admin types cannot be derived from the type alone")
    void userAdminResultComesFromTheEvent(AuditEventType eventType) {
        assertThatThrownBy(() -> AuditEventFactory.resultOf(eventType)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a long user agent of a user admin event is truncated")
    void userAdminUserAgentIsTruncated() {
        AuditEvent audit = AuditEventFactory.from(UserAdminAuditEvent.succeeded(
                AdminOperation.RESUME, 3, 4, OCCURRED_AT, new RequestOrigin("192.0.2.50", "😀".repeat(400), null)));

        assertThat(audit.getUserAgent().codePointCount(0, audit.getUserAgent().length()))
                .isLessThanOrEqualTo(AuditText.MAX_USER_AGENT_LENGTH);
    }

    @Test
    @DisplayName(
            "an initial admin creation is a successful startup row with only the target user and the system source")
    void initialAdminCreated() {
        AuditEvent audit = AuditEventFactory.from(new InitialAdminCreatedEvent(5L, OCCURRED_AT));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.INITIAL_ADMIN_CREATED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(audit.getTargetUserId()).isEqualTo(5L);
        assertThat(audit.getSourceIp()).isEqualTo("system").isEqualTo(AuditEventFactory.SYSTEM_SOURCE_IP);
        assertThat(audit.getActorUserId()).isNull();
        assertThat(audit.getRejectionKind()).isNull();
        assertThat(audit.getEnteredEmail()).isNull();
        assertThat(audit.getFailureReason()).isNull();
        assertThat(audit.getUserAgent()).isNull();
        assertThat(audit.getRequestPath()).isNull();
        assertThat(audit.getTraceId()).isNull();
        assertThat(audit.getDslHash()).isNull();
        assertThat(audit.getDslSource()).isNull();
        assertThat(audit.getTargetInvitationId()).isNull();
    }

    @Test
    @DisplayName("an initial admin rescue records the conditions in the declared order in the rejection kind column")
    void initialAdminRescued() {
        Set<InitialAdminRescueCondition> conditions =
                Set.of(InitialAdminRescueCondition.PASSWORD, InitialAdminRescueCondition.SUSPENDED);

        AuditEvent audit = AuditEventFactory.from(new InitialAdminRescuedEvent(6L, conditions, OCCURRED_AT));

        assertThat(audit.getEventType()).isEqualTo(AuditEventType.INITIAL_ADMIN_RESCUED);
        assertThat(audit.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(audit.getTargetUserId()).isEqualTo(6L);
        assertThat(audit.getSourceIp()).isEqualTo("system");
        assertThat(audit.getRejectionKind()).isEqualTo("SUSPENDED+PASSWORD");
        assertThat(audit.getActorUserId()).isNull();
        assertThat(audit.getEnteredEmail()).isNull();
        assertThat(audit.getFailureReason()).isNull();
        assertThat(audit.getUserAgent()).isNull();
        assertThat(audit.getRequestPath()).isNull();
        assertThat(audit.getTraceId()).isNull();
        assertThat(audit.getDslHash()).isNull();
        assertThat(audit.getDslSource()).isNull();
        assertThat(audit.getTargetInvitationId()).isNull();
    }

    @Test
    @DisplayName("a rescue of all three conditions fits into the rejection kind column")
    void initialAdminRescuedAllConditions() {
        AuditEvent audit = AuditEventFactory.from(
                new InitialAdminRescuedEvent(6L, EnumSet.allOf(InitialAdminRescueCondition.class), OCCURRED_AT));

        assertThat(audit.getRejectionKind()).isEqualTo("SUSPENDED+NO_ADMIN+PASSWORD");
        assertThat(audit.getRejectionKind().length()).isLessThanOrEqualTo(32);
    }

    @Test
    @DisplayName("the startup types are successes and the system source fits the source column")
    void startupTypeResults() {
        assertThat(AuditEventFactory.resultOf(AuditEventType.INITIAL_ADMIN_CREATED))
                .isEqualTo(AuditResult.SUCCESS);
        assertThat(AuditEventFactory.resultOf(AuditEventType.INITIAL_ADMIN_RESCUED))
                .isEqualTo(AuditResult.SUCCESS);
        assertThat(AuditEventFactory.SYSTEM_SOURCE_IP.length()).isLessThanOrEqualTo(45);
    }
}
