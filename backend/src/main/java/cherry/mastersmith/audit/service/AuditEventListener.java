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
package cherry.mastersmith.audit.service;

import cherry.mastersmith.access.domain.AdminAccessDeniedEvent;
import cherry.mastersmith.audit.domain.AuditEvent;
import cherry.mastersmith.audit.domain.AuditEventFactory;
import cherry.mastersmith.audit.domain.AuditEventType;
import cherry.mastersmith.audit.domain.AuditResult;
import cherry.mastersmith.auth.domain.AuthenticationEvent;
import cherry.mastersmith.dslmanage.domain.DslOperationEvent;
import cherry.mastersmith.invitation.domain.InvitationCancelledEvent;
import cherry.mastersmith.invitation.domain.InvitationIssuedEvent;
import cherry.mastersmith.invitation.domain.InvitationResentEvent;
import cherry.mastersmith.invitation.domain.RegistrationCompletedEvent;
import cherry.mastersmith.invitation.domain.RegistrationFailedEvent;
import cherry.mastersmith.user.domain.PasswordChangedEvent;
import cherry.mastersmith.useradmin.domain.UserAdminAuditEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * U2 の認証の出来事と U3 のアクセス拒否の出来事、DSL の操作の出来事（Intent 260923-dsl-schema-loader の U4）、パスワードの変更の
 * 出来事（Intent 260925-user-management の U2）、招待と登録の出来事（同じ Intent の U3）、利用者の管理の操作の出来事（Intent
 * 260930-user-admin の U3）を受け取り、監査イベントを1件ずつ追記する（BR1.1〜BR1.6、BR3.1、BR3.2）。
 *
 * <p>どちらの受け取りも {@link TransactionalEventListener} の確定の後（{@link TransactionPhase#AFTER_COMMIT}）で、
 * トランザクションが無いときも受け取る設定（{@code fallbackExecution = true}）にする
 * （{@code nfr-design/reliability-design.md} 1章）。
 *
 * <ul>
 *   <li>U2 の出来事はトランザクションの中で知らされるため、確定の後に同じスレッドで受け取る。取り消されたら受け取りは呼ばれず、
 *       記録もされない（BR1.4）。
 *   <li>U3 の出来事は 401／403 の処理（トランザクションの外）で知らされるため、要求と同じスレッドで、応答を書く前にその場で
 *       受け取る。
 * </ul>
 *
 * <p>ほかの受け取り側の失敗で監査の記録が飛ばないよう、最優先の順番で受け取る。組み立て・トランザクションの開始・追記・確定の
 * すべてを受け止め、呼び出し元へ伝えない。再試行しない（BR3.1、NFR10.1）。
 */
@Component
public class AuditEventListener {

    /** 遅い書き込みとみなす時間（ミリ秒。設定にはしない。計画の C5）。 */
    static final long SLOW_WRITE_THRESHOLD_MILLIS = 200;

    /** 書き込みの失敗の ERROR のメッセージ（固定の文。例外のメッセージは使わない。NFR10.3）。 */
    static final String FAILURE_MESSAGE = "監査イベントの記録に失敗しました";

    /** 遅い書き込みの WARN のメッセージ。 */
    static final String SLOW_WRITE_MESSAGE = "監査イベントの記録に時間がかかりました";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditEventRecorder recorder;

    private final LongSupplier nanoTime;

    /**
     * 作る。
     *
     * @param recorder 新しいトランザクションでの追記
     * @param nanoTime 書き込みの時間の測り方
     */
    public AuditEventListener(AuditEventRecorder recorder, LongSupplier nanoTime) {
        this.recorder = recorder;
        this.nanoTime = nanoTime;
    }

    /**
     * U2 の認証の出来事を受け取り、監査イベントを追記する。
     *
     * @param event 認証の出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuthenticationEvent(AuthenticationEvent event) {
        record(() -> AuditEventFactory.from(event), () -> fields(event));
    }

    /**
     * U3 のアクセス拒否の出来事を受け取り、監査イベントを追記する。
     *
     * @param event アクセス拒否の出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAdminAccessDeniedEvent(AdminAccessDeniedEvent event) {
        record(() -> AuditEventFactory.from(event), () -> fields(event));
    }

    /**
     * DSL の操作の出来事を受け取り、監査イベントを追記する（Intent 260923-dsl-schema-loader の U4、契約 C7、BR7.1〜BR7.3）。
     *
     * <p>U4 は出来事を元の操作の確定の後に、トランザクションの外で知らせるため、要求と同じスレッドでその場で受け取る。巻き戻った操作の
     * 出来事は知らされない。
     *
     * @param event DSL の操作の出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDslOperationEvent(DslOperationEvent event) {
        // 組み立てが失敗するのは出来事が無いときだけ（出来事の必須の項目は出来事の側で確かめている）ため、載せる項目は無い。
        record(() -> AuditEventFactory.from(event), () -> fields(null, null, null, null, null, null, null, null, null));
    }

    /**
     * パスワードの変更の出来事を受け取り、監査イベントを追記する（Intent 260925-user-management の U2、契約 C8、BR7.2・BR7.3）。
     *
     * <ul>
     *   <li>成功は変更のトランザクションの中で知らされるため、確定の後に受け取る。巻き戻った変更の出来事は受け取らない
     *   <li>今のパスワードの誤りはトランザクションの外で知らされるため、要求と同じスレッドでその場で受け取る
     * </ul>
     *
     * @param event パスワードの変更の出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordChangedEvent(PasswordChangedEvent event) {
        record(() -> AuditEventFactory.from(event), () -> fields(event));
    }

    /**
     * 招待したことの出来事を受け取り、監査イベントを追記する（Intent 260925-user-management の U3、契約 C8、BR8.1・BR8.5）。招待の
     * トランザクションの中で知らされるため、確定の後に受け取る。
     *
     * @param event 出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInvitationIssuedEvent(InvitationIssuedEvent event) {
        record(
                () -> AuditEventFactory.from(event),
                () -> invitationFields(AuditEventType.INVITATION_ISSUED, event == null ? null : event.invitationId()));
    }

    /**
     * 招待を送り直したことの出来事を受け取り、監査イベントを追記する（U3、BR8.1・BR8.5）。
     *
     * @param event 出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInvitationResentEvent(InvitationResentEvent event) {
        record(
                () -> AuditEventFactory.from(event),
                () -> invitationFields(AuditEventType.INVITATION_RESENT, event == null ? null : event.invitationId()));
    }

    /**
     * 招待を取り消したことの出来事を受け取り、監査イベントを追記する（U3、BR8.1・BR8.5）。
     *
     * @param event 出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInvitationCancelledEvent(InvitationCancelledEvent event) {
        record(
                () -> AuditEventFactory.from(event),
                () -> invitationFields(
                        AuditEventType.INVITATION_CANCELLED, event == null ? null : event.invitationId()));
    }

    /**
     * 登録を完了したことの出来事を受け取り、監査イベントを追記する（U3、BR8.2・BR8.5）。巻き戻った完了の出来事は受け取らない。
     *
     * @param event 出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRegistrationCompletedEvent(RegistrationCompletedEvent event) {
        record(
                () -> AuditEventFactory.from(event),
                () -> invitationFields(
                        AuditEventType.REGISTRATION_COMPLETED, event == null ? null : event.invitationId()));
    }

    /**
     * 登録の完了の要求のリンクの拒否の出来事を受け取り、監査イベントを追記する（U3、BR8.3・BR8.5）。トランザクションの外で知らされる
     * ため、要求と同じスレッドでその場で受け取る。
     *
     * @param event 出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRegistrationFailedEvent(RegistrationFailedEvent event) {
        record(
                () -> AuditEventFactory.from(event),
                () -> invitationFields(
                        AuditEventType.REGISTRATION_FAILED, event == null ? null : event.invitationId()));
    }

    /**
     * 利用者の管理の操作の出来事を受け取り、監査イベントを追記する（Intent 260930-user-admin の U3、契約 C6、BR6.1〜BR6.3）。
     *
     * <p>成功も業務の拒否も、業務処理のトランザクションの中で知らされ、確定の後に受け取る（拒否は書き込みなしで確定する）。巻き戻った
     * 操作の出来事は受け取らない。書き込みの失敗は応答を変えず、ERROR を1回出す。
     *
     * @param event 出来事
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserAdminAuditEvent(UserAdminAuditEvent event) {
        record(() -> AuditEventFactory.from(event), () -> fields(event));
    }

    /**
     * 利用者の管理の操作の出来事の項目（組み立てに失敗したときに載せる。メールアドレス・氏名は持たない）。監査の種類・結果・失敗の理由は、
     * 組み立てに成功したときの {@link #fields(AuditEvent)} と同じ値の形（{@link AuditEventType}・{@link AuditResult}・
     * {@code AuditFailureReason}）に、{@link AuditEventFactory} と同じ対応で写す。
     */
    private static Map<String, Object> fields(UserAdminAuditEvent event) {
        if (event == null) {
            Map<String, Object> fields = fields(null, null, null, null, null, null, null, null, null);
            fields.put("actorUserId", null);
            fields.putAll(targetFields(null, null));
            return fields;
        }
        Map<String, Object> fields = fields(
                AuditEventFactory.userAdminEventTypeOf(event.operation()),
                event.succeeded() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                event.occurredAt(),
                null,
                AuditEventFactory.userAdminFailureReasonOf(event.failure()),
                event.sourceIp(),
                event.userAgent(),
                null,
                event.traceId());
        fields.put("actorUserId", event.actorUserId());
        fields.putAll(targetFields(event.targetUserId(), null));
        return fields;
    }

    /** 招待と登録の出来事の組み立てに失敗したときに載せる項目（メールアドレス・トークンは持たない）。 */
    private static Map<String, Object> invitationFields(AuditEventType eventType, Long invitationId) {
        Map<String, Object> fields = fields(eventType, null, null, null, null, null, null, null, null);
        fields.putAll(targetFields(null, invitationId));
        return fields;
    }

    /**
     * 監査イベントを組み立てて追記し、失敗を受け止める。
     *
     * @param builder 監査イベントの組み立て
     * @param fallbackFields 組み立てに失敗したときに ERROR に載せる項目
     */
    private void record(Supplier<AuditEvent> builder, Supplier<Map<String, Object>> fallbackFields) {
        AuditEvent auditEvent = null;
        try {
            auditEvent = builder.get();
            long start = nanoTime.getAsLong();
            recorder.record(auditEvent);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - start);
            if (elapsedMillis > SLOW_WRITE_THRESHOLD_MILLIS) {
                LOGGER.atWarn()
                        .addKeyValue("auditEventType", auditEvent.getEventType())
                        .addKeyValue("elapsedMs", elapsedMillis)
                        .log(SLOW_WRITE_MESSAGE);
            }
            // 成功したときは、監査の内容をアプリのログに出さない（二重の記録にしない。BR3.2）。
        } catch (RuntimeException e) {
            logFailure(auditEvent == null ? fallbackFields.get() : fields(auditEvent), e);
        }
    }

    /**
     * 書き込みの失敗を ERROR で1回出す。記録しようとした全項目（メールアドレスを含む）をキーと値で載せる（NFR10.3）。
     *
     * @param fields 記録しようとした項目
     * @param e 受け止めた例外
     */
    private void logFailure(Map<String, Object> fields, RuntimeException e) {
        LoggingEventBuilder builder = LOGGER.atError();
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            builder = builder.addKeyValue(field.getKey(), field.getValue());
        }
        builder.addKeyValue("exceptionType", e.getClass().getName()).setCause(e).log(FAILURE_MESSAGE);
    }

    /**
     * 記録しようとした監査イベントの項目を並べる。操作した人がいる出来事は、DSL の操作なら DSL の項目を、それ以外（パスワードの変更）
     * なら操作した人だけを足す。対象（利用者・招待）を持つ出来事は対象の2項目を足す（既存の出来事の項目は変えない）。
     */
    private static Map<String, Object> fields(AuditEvent auditEvent) {
        Map<String, Object> fields = fields(
                auditEvent.getEventType(),
                auditEvent.getResult(),
                auditEvent.getOccurredAt(),
                auditEvent.getEnteredEmail(),
                auditEvent.getFailureReason(),
                auditEvent.getSourceIp(),
                auditEvent.getUserAgent(),
                auditEvent.getRequestPath(),
                auditEvent.getTraceId());
        if (auditEvent.getActorUserId() != null) {
            if (isDslOperation(auditEvent.getEventType())) {
                fields.putAll(dslFields(
                        auditEvent.getActorUserId(),
                        auditEvent.getDslHash(),
                        auditEvent.getDslSource(),
                        auditEvent.getRejectionKind()));
            } else {
                fields.put("actorUserId", auditEvent.getActorUserId());
            }
        }
        if (auditEvent.getTargetUserId() != null || auditEvent.getTargetInvitationId() != null) {
            fields.putAll(targetFields(auditEvent.getTargetUserId(), auditEvent.getTargetInvitationId()));
        }
        return fields;
    }

    /** DSL の操作の種類かを返す（網羅の {@code switch}。種類が増えたときにコンパイルで気づけるようにする）。 */
    private static boolean isDslOperation(AuditEventType eventType) {
        return switch (eventType) {
            case DSL_GENERATED, DSL_SUBMITTED, DSL_SUBMISSION_REJECTED, DSL_APPLIED, DSL_PREVIEW_DISCARDED -> true;
            case LOGIN_SUCCEEDED,
                    LOGIN_FAILED,
                    LOGGED_OUT,
                    ACCESS_DENIED,
                    PASSWORD_CHANGED,
                    INVITATION_ISSUED,
                    INVITATION_RESENT,
                    INVITATION_CANCELLED,
                    REGISTRATION_COMPLETED,
                    REGISTRATION_FAILED,
                    USER_ADMIN_GRANTED,
                    USER_ADMIN_REVOKED,
                    USER_SUSPENDED,
                    USER_RESUMED,
                    LOGIN_FAILURES_RESET -> false;
        };
    }

    /** 対象の項目（Intent 260925-user-management の契約 C8）。 */
    private static Map<String, Object> targetFields(Object targetUserId, Object targetInvitationId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("targetUserId", targetUserId);
        fields.put("targetInvitationId", targetInvitationId);
        return fields;
    }

    /** パスワードの変更の出来事の項目（組み立てに失敗したときに載せる。パスワード・ハッシュ・メールアドレスは持たない）。 */
    private static Map<String, Object> fields(PasswordChangedEvent event) {
        if (event == null) {
            Map<String, Object> fields = fields(null, null, null, null, null, null, null, null, null);
            fields.put("actorUserId", null);
            fields.putAll(targetFields(null, null));
            return fields;
        }
        Map<String, Object> fields = fields(
                AuditEventType.PASSWORD_CHANGED,
                event.result(),
                event.occurredAt(),
                null,
                event.failureReason(),
                event.sourceIp(),
                event.userAgent(),
                null,
                event.traceId());
        fields.put("actorUserId", event.userId());
        fields.putAll(targetFields(event.userId(), null));
        return fields;
    }

    /** DSL の操作の項目（本文と接続先は持たない）。 */
    private static Map<String, Object> dslFields(
            Object actorUserId, String dslHash, String dslSource, String rejectionKind) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("actorUserId", actorUserId);
        fields.put("dslHash", dslHash);
        fields.put("dslSource", dslSource);
        fields.put("rejectionKind", rejectionKind);
        return fields;
    }

    private static Map<String, Object> fields(AuthenticationEvent event) {
        if (event == null) {
            return fields(null, null, null, null, null, null, null, null, null);
        }
        return fields(
                event.eventType(),
                null,
                event.occurredAt(),
                event.enteredEmail(),
                event.failureReason(),
                event.sourceIp(),
                event.userAgent(),
                null,
                event.traceId());
    }

    private static Map<String, Object> fields(AdminAccessDeniedEvent event) {
        if (event == null) {
            return fields(null, null, null, null, null, null, null, null, null);
        }
        return fields(
                event.eventType(),
                event.result(),
                event.occurredAt(),
                event.enteredEmail(),
                event.failureReason(),
                event.sourceIp(),
                event.userAgent(),
                event.requestPath(),
                event.traceId());
    }

    private static Map<String, Object> fields(
            Object eventType,
            Object result,
            Object occurredAt,
            String enteredEmail,
            Object failureReason,
            String sourceIp,
            String userAgent,
            String requestPath,
            String traceId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("auditEventType", eventType);
        fields.put("result", result);
        fields.put("occurredAt", occurredAt);
        fields.put("enteredEmail", enteredEmail);
        fields.put("failureReason", failureReason);
        fields.put("sourceIp", sourceIp);
        fields.put("userAgent", userAgent);
        fields.put("requestPath", requestPath);
        fields.put("auditTraceId", traceId);
        return fields;
    }
}
