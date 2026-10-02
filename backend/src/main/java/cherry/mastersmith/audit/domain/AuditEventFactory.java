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

import cherry.mastersmith.access.domain.AccessDeniedReason;
import cherry.mastersmith.access.domain.AccessEventType;
import cherry.mastersmith.access.domain.AccessResult;
import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.auth.domain.AuthenticationEventType;
import cherry.mastersmith.auth.domain.LoginFailureReason;
import cherry.mastersmith.dslmanage.domain.DslOperationEvent;
import cherry.mastersmith.dslmanage.domain.DslOperationType;
import cherry.mastersmith.invitation.domain.InvitationCancelledEvent;
import cherry.mastersmith.invitation.domain.InvitationIssuedEvent;
import cherry.mastersmith.invitation.domain.InvitationResentEvent;
import cherry.mastersmith.invitation.domain.LinkRejection;
import cherry.mastersmith.invitation.domain.RegistrationCompletedEvent;
import cherry.mastersmith.invitation.domain.RegistrationFailedEvent;
import cherry.mastersmith.user.domain.PasswordChangeFailureReason;
import cherry.mastersmith.user.domain.PasswordChangeOutcome;
import cherry.mastersmith.user.domain.PasswordChangedEvent;
import java.time.Instant;

/**
 * 受け取った出来事から監査イベントを作る（BR1.1、BR1.2、BR1.5、BR2.1）。DB にも時計にも触れない純粋な関数である。
 *
 * <p>種類から結果を決め（BR1.2）、日時は出来事の日時をそのまま使う（BR1.5）。攻撃者が決められる値（メールアドレス・
 * User-Agent・要求のパス）は {@link AuditText} で切り詰める。要求のパスはアクセスの拒否のときだけ記録し、認証の出来事では
 * 空にする（{@code nfr-design/security-design.md} 1章・6章）。
 *
 * <p>U2 の出来事の利用者IDは、監査ログの記録項目に含まれないため記録しない（{@code functional-design/entities.md}）。
 * U3 の出来事のメールアドレスは、文字列化が伏せ字になるため必ず {@link AdminAccessDeniedEvent#enteredEmail()} で取る。
 *
 * <p>パスワードの変更（Intent 260925-user-management の U2）の出来事は、操作した人と対象の利用者に本人を記録する。
 *
 * <p>招待と登録（Intent 260925-user-management の U3）の出来事は、招待の管理は操作した管理者と対象の招待、登録の完了は対象の招待と
 * 作った利用者、登録の失敗は対象の招待（見つかったときだけ）と理由を記録する。メールアドレス・トークンは出来事が持たないため、記録にも
 * 入らない（U3 の BR8.1〜BR8.3・BR8.6）。
 *
 * <p>種類と理由の写し取りは網羅の {@code switch} で書く。U2・U3 が値を増やしたときに、コンパイルで気づけるようにするため。
 */
public final class AuditEventFactory {

    private AuditEventFactory() {}

    /**
     * U2 の認証の出来事から監査イベントを作る。
     *
     * @param event 認証の出来事
     * @return 監査イベント
     */
    public static AuditEvent from(AuthenticationEvent event) {
        AuditEventType eventType = eventTypeOf(event.eventType());
        return new AuditEvent(
                event.occurredAt(),
                eventType,
                resultOf(eventType),
                AuditText.enteredEmail(event.enteredEmail()),
                failureReasonOf(event.failureReason()),
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                null,
                event.traceId());
    }

    /**
     * U3 のアクセス拒否の出来事から監査イベントを作る。
     *
     * @param event アクセス拒否の出来事
     * @return 監査イベント
     */
    public static AuditEvent from(AdminAccessDeniedEvent event) {
        return new AuditEvent(
                event.occurredAt(),
                eventTypeOf(event.eventType()),
                resultOf(event.result()),
                AuditText.enteredEmail(event.enteredEmail()),
                failureReasonOf(event.failureReason()),
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                AuditText.requestPath(event.requestPath()),
                event.traceId());
    }

    /**
     * DSL の操作の出来事から監査イベントを作る（Intent 260923-dsl-schema-loader の U4、契約 C7、BR7.1・BR7.2）。
     *
     * <p>DSL の本文と対象DB の接続先は出来事が持たないため、記録にも入らない。User-Agent は上限まで切り詰める。
     *
     * @param event DSL の操作の出来事
     * @return 監査イベント
     */
    public static AuditEvent from(DslOperationEvent event) {
        AuditEventType eventType = eventTypeOf(event.type());
        return new AuditEvent(
                event.occurredAt(),
                eventType,
                resultOf(eventType),
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                event.traceId(),
                event.actorUserId(),
                event.dslHash(),
                event.source() == null ? null : event.source().name(),
                event.rejectionKind());
    }

    /**
     * パスワードの変更の出来事から監査イベントを作る（Intent 260925-user-management の U2、契約 C8、BR7.2）。
     *
     * <p>操作した人と対象の利用者の両方に本人を記録する（契約 C8 の PASSWORD_CHANGED の項目に対象の利用者を足した差は、U2 のコード生成の
     * 記録に書く）。メールアドレス・要求のパス・対象の招待・DSL の項目は空。User-Agent は上限まで切り詰める。
     *
     * @param event パスワードの変更の出来事
     * @return 監査イベント
     */
    public static AuditEvent from(PasswordChangedEvent event) {
        return AuditEvent.withTarget(
                event.occurredAt(),
                AuditEventType.PASSWORD_CHANGED,
                resultOf(event.result()),
                failureReasonOf(event.failureReason()),
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                event.traceId(),
                event.userId(),
                event.userId(),
                null);
    }

    /**
     * 招待したことの出来事から監査イベントを作る（U3 の BR8.1）。
     *
     * @param event 出来事
     * @return 監査イベント
     */
    public static AuditEvent from(InvitationIssuedEvent event) {
        return invitationAdmin(
                AuditEventType.INVITATION_ISSUED,
                event.invitationId(),
                event.actorUserId(),
                event.occurredAt(),
                event.sourceIp(),
                event.userAgent(),
                event.traceId());
    }

    /**
     * 招待を送り直したことの出来事から監査イベントを作る（U3 の BR8.1）。
     *
     * @param event 出来事
     * @return 監査イベント
     */
    public static AuditEvent from(InvitationResentEvent event) {
        return invitationAdmin(
                AuditEventType.INVITATION_RESENT,
                event.invitationId(),
                event.actorUserId(),
                event.occurredAt(),
                event.sourceIp(),
                event.userAgent(),
                event.traceId());
    }

    /**
     * 招待を取り消したことの出来事から監査イベントを作る（U3 の BR8.1）。
     *
     * @param event 出来事
     * @return 監査イベント
     */
    public static AuditEvent from(InvitationCancelledEvent event) {
        return invitationAdmin(
                AuditEventType.INVITATION_CANCELLED,
                event.invitationId(),
                event.actorUserId(),
                event.occurredAt(),
                event.sourceIp(),
                event.userAgent(),
                event.traceId());
    }

    /**
     * 登録を完了したことの出来事から監査イベントを作る（U3 の BR8.2。操作した人は空）。
     *
     * @param event 出来事
     * @return 監査イベント
     */
    public static AuditEvent from(RegistrationCompletedEvent event) {
        return AuditEvent.withTarget(
                event.occurredAt(),
                AuditEventType.REGISTRATION_COMPLETED,
                resultOf(AuditEventType.REGISTRATION_COMPLETED),
                null,
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                event.traceId(),
                null,
                event.userId(),
                event.invitationId());
    }

    /**
     * 登録の完了の要求のリンクの拒否の出来事から監査イベントを作る（U3 の BR8.3。操作した人は空、対象の招待は見つかったときだけ）。
     *
     * @param event 出来事
     * @return 監査イベント
     */
    public static AuditEvent from(RegistrationFailedEvent event) {
        return AuditEvent.withTarget(
                event.occurredAt(),
                AuditEventType.REGISTRATION_FAILED,
                resultOf(AuditEventType.REGISTRATION_FAILED),
                failureReasonOf(event.reason()),
                event.sourceIp(),
                AuditText.userAgent(event.userAgent()),
                event.traceId(),
                null,
                null,
                event.invitationId());
    }

    private static AuditEvent invitationAdmin(
            AuditEventType eventType,
            long invitationId,
            long actorUserId,
            Instant occurredAt,
            String sourceIp,
            String userAgent,
            String traceId) {
        return AuditEvent.withTarget(
                occurredAt,
                eventType,
                resultOf(eventType),
                null,
                sourceIp,
                AuditText.userAgent(userAgent),
                traceId,
                actorUserId,
                null,
                invitationId);
    }

    private static AuditFailureReason failureReasonOf(LinkRejection reason) {
        return switch (reason) {
            case INVITATION_EXPIRED -> AuditFailureReason.INVITATION_EXPIRED;
            case INVITATION_ALREADY_USED -> AuditFailureReason.INVITATION_ALREADY_USED;
            case INVITATION_CANCELLED -> AuditFailureReason.INVITATION_CANCELLED;
            case INVITATION_NOT_FOUND -> AuditFailureReason.INVITATION_NOT_FOUND;
            case EMAIL_ALREADY_REGISTERED -> AuditFailureReason.EMAIL_ALREADY_REGISTERED;
        };
    }

    private static AuditEventType eventTypeOf(DslOperationType eventType) {
        return switch (eventType) {
            case DSL_GENERATED -> AuditEventType.DSL_GENERATED;
            case DSL_SUBMITTED -> AuditEventType.DSL_SUBMITTED;
            case DSL_SUBMISSION_REJECTED -> AuditEventType.DSL_SUBMISSION_REJECTED;
            case DSL_APPLIED -> AuditEventType.DSL_APPLIED;
            case DSL_PREVIEW_DISCARDED -> AuditEventType.DSL_PREVIEW_DISCARDED;
        };
    }

    private static AuditEventType eventTypeOf(AuthenticationEventType eventType) {
        return switch (eventType) {
            case LOGIN_SUCCEEDED -> AuditEventType.LOGIN_SUCCEEDED;
            case LOGIN_FAILED -> AuditEventType.LOGIN_FAILED;
            case LOGGED_OUT -> AuditEventType.LOGGED_OUT;
        };
    }

    private static AuditEventType eventTypeOf(AccessEventType eventType) {
        return switch (eventType) {
            case ACCESS_DENIED -> AuditEventType.ACCESS_DENIED;
        };
    }

    /**
     * 監査イベントの種類から結果を決める（BR1.2）。
     *
     * <p>パスワードの変更（{@link AuditEventType#PASSWORD_CHANGED}）は成功も失敗も同じ種類で、結果は出来事が持つため、種類からは
     * 決められない（呼び出すと想定外の誤り）。
     *
     * @param eventType 種類
     * @return 結果
     * @throws IllegalArgumentException 種類から結果を決められないとき
     */
    public static AuditResult resultOf(AuditEventType eventType) {
        return switch (eventType) {
            case LOGIN_SUCCEEDED,
                    LOGGED_OUT,
                    DSL_GENERATED,
                    DSL_SUBMITTED,
                    DSL_APPLIED,
                    DSL_PREVIEW_DISCARDED,
                    INVITATION_ISSUED,
                    INVITATION_RESENT,
                    INVITATION_CANCELLED,
                    REGISTRATION_COMPLETED -> AuditResult.SUCCESS;
            case LOGIN_FAILED, ACCESS_DENIED, DSL_SUBMISSION_REJECTED, REGISTRATION_FAILED -> AuditResult.FAILURE;
            case PASSWORD_CHANGED -> throw new IllegalArgumentException("PASSWORD_CHANGED の結果は種類から決められません（出来事が持つ）");
        };
    }

    private static AuditResult resultOf(PasswordChangeOutcome result) {
        return switch (result) {
            case SUCCESS -> AuditResult.SUCCESS;
            case FAILURE -> AuditResult.FAILURE;
        };
    }

    private static AuditResult resultOf(AccessResult result) {
        return switch (result) {
            case FAILURE -> AuditResult.FAILURE;
        };
    }

    private static AuditFailureReason failureReasonOf(LoginFailureReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case USER_NOT_FOUND -> AuditFailureReason.USER_NOT_FOUND;
            case PASSWORD_MISMATCH -> AuditFailureReason.PASSWORD_MISMATCH;
            case ACCOUNT_LOCKED -> AuditFailureReason.ACCOUNT_LOCKED;
            case ACCOUNT_SUSPENDED -> AuditFailureReason.ACCOUNT_SUSPENDED;
        };
    }

    private static AuditFailureReason failureReasonOf(PasswordChangeFailureReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case CURRENT_PASSWORD_MISMATCH -> AuditFailureReason.CURRENT_PASSWORD_MISMATCH;
        };
    }

    private static AuditFailureReason failureReasonOf(AccessDeniedReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case NOT_ADMIN -> AuditFailureReason.NOT_ADMIN;
            case TOKEN_MISSING -> AuditFailureReason.TOKEN_MISSING;
            case TOKEN_MALFORMED -> AuditFailureReason.TOKEN_MALFORMED;
            case TOKEN_INVALID -> AuditFailureReason.TOKEN_INVALID;
            case USER_NOT_FOUND -> AuditFailureReason.USER_NOT_FOUND;
        };
    }
}
