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
import cherry.mastersmith.auth.domain.AuthenticationEvent;
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
 * U2 の認証の出来事と U3 のアクセス拒否の出来事を受け取り、監査イベントを1件ずつ追記する（BR1.1〜BR1.6、BR3.1、BR3.2）。
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

    private static Map<String, Object> fields(AuditEvent auditEvent) {
        return fields(
                auditEvent.getEventType(),
                auditEvent.getResult(),
                auditEvent.getOccurredAt(),
                auditEvent.getEnteredEmail(),
                auditEvent.getFailureReason(),
                auditEvent.getSourceIp(),
                auditEvent.getUserAgent(),
                auditEvent.getRequestPath(),
                auditEvent.getTraceId());
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
