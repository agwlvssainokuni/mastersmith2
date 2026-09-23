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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/**
 * 監査イベント（表 {@code audit_events}）。認証とアクセス制御の出来事を1件ずつ記録する（FR9.1、FR9.2）。
 *
 * <p>追記だけの記録であり、作ったあとに値を変える手段を持たない。JPA でも更新させないため、{@link Immutable} と各列の
 * {@code updatable = false} を指定する（BR4.1、NFR3.2）。
 *
 * <p>パスワード・トークン・ハッシュ値・Authorization ヘッダーの項目を持たない（BR2.3、NFR3.1）。文字列化ではメールアドレスを
 * 伏せる（U1 のメソッドの呼び出しの追跡が引数・戻り値を文字列にするため）。
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id")
    private Long auditEventId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @Column(name = "entered_email", updatable = false, length = 508)
    private String enteredEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", updatable = false, length = 32)
    private AuditFailureReason failureReason;

    @Column(name = "source_ip", nullable = false, updatable = false, length = 45)
    private String sourceIp;

    @Column(name = "user_agent", updatable = false, length = 1024)
    private String userAgent;

    @Column(name = "request_path", updatable = false, length = 1024)
    private String requestPath;

    @Column(name = "trace_id", updatable = false, length = 64)
    private String traceId;

    /** JPA が使う。 */
    protected AuditEvent() {}

    /**
     * 監査イベントを作る。値の切り詰めは {@link AuditEventFactory} が済ませたものを受け取る。
     *
     * @param occurredAt 出来事が起きた日時（BR1.5）
     * @param eventType 種類
     * @param result 結果（BR1.2）
     * @param enteredEmail 入力された（または特定できた）メールアドレス（分からなければ null）
     * @param failureReason 失敗の理由（成功なら null）
     * @param sourceIp 接続元IP
     * @param userAgent User-Agent（無ければ null）
     * @param requestPath 要求のパス（アクセスの拒否のときだけ。それ以外は null）
     * @param traceId トレースID（無ければ null）
     */
    public AuditEvent(
            Instant occurredAt,
            AuditEventType eventType,
            AuditResult result,
            String enteredEmail,
            AuditFailureReason failureReason,
            String sourceIp,
            String userAgent,
            String requestPath,
            String traceId) {
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.result = Objects.requireNonNull(result, "result");
        this.enteredEmail = enteredEmail;
        this.failureReason = failureReason;
        this.sourceIp = Objects.requireNonNull(sourceIp, "sourceIp");
        this.userAgent = userAgent;
        this.requestPath = requestPath;
        this.traceId = traceId;
    }

    /**
     * 監査イベントの ID を返す。
     *
     * @return ID（追記の前は null）
     */
    public Long getAuditEventId() {
        return auditEventId;
    }

    /**
     * 出来事が起きた日時を返す。
     *
     * @return 日時
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * 種類を返す。
     *
     * @return 種類
     */
    public AuditEventType getEventType() {
        return eventType;
    }

    /**
     * 結果を返す。
     *
     * @return 結果
     */
    public AuditResult getResult() {
        return result;
    }

    /**
     * 入力されたメールアドレスを返す。
     *
     * @return メールアドレス（分からなければ null）
     */
    public String getEnteredEmail() {
        return enteredEmail;
    }

    /**
     * 失敗の理由を返す。
     *
     * @return 失敗の理由（成功なら null）
     */
    public AuditFailureReason getFailureReason() {
        return failureReason;
    }

    /**
     * 接続元IPを返す。
     *
     * @return 接続元IP
     */
    public String getSourceIp() {
        return sourceIp;
    }

    /**
     * User-Agent を返す。
     *
     * @return User-Agent（無ければ null）
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * 要求のパスを返す。
     *
     * @return 要求のパス（アクセスの拒否のとき以外は null）
     */
    public String getRequestPath() {
        return requestPath;
    }

    /**
     * トレースIDを返す。
     *
     * @return トレースID（無ければ null）
     */
    public String getTraceId() {
        return traceId;
    }

    /** メールアドレスを伏せて文字列にする（アプリのログにメールアドレスを出さないため）。 */
    @Override
    public String toString() {
        return "AuditEvent[auditEventId=" + auditEventId
                + ", occurredAt=" + occurredAt
                + ", eventType=" + eventType
                + ", result=" + result
                + ", enteredEmail=" + (enteredEmail == null ? "null" : "***")
                + ", failureReason=" + failureReason
                + ", sourceIp=" + sourceIp
                + ", userAgent=" + userAgent
                + ", requestPath=" + requestPath
                + ", traceId=" + traceId
                + "]";
    }
}
