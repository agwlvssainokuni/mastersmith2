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
 * <p>DSL の操作の出来事（Intent 260923-dsl-schema-loader の U4、契約 C7）では、操作した管理者の利用者 ID・DSL の識別・出どころ・
 * 受け付けなかった投入の理由の種類も記録する（V6 で足した NULL を許す列）。DSL の本文と対象DB の接続先は持たない。
 *
 * <p>対象の利用者・対象の招待（V7 で足した NULL を許す列。Intent 260925-user-management の契約 C8）を持つ。パスワードの変更では
 * 操作した人と対象の利用者の両方に本人を記録する。既存の出来事では空のまま。
 *
 * <p>起動時の出来事（初期管理者の作成・救済。Intent 261004-safety-carryover の FR1.5・FR1.6・FR1.6a）は、要求を持たないため
 * 接続元に決まった値 {@code system} を入れ、操作した人・メールアドレス・User-Agent・要求のパス・トレースID・DSL の項目・対象の招待は
 * 空にする。救済の行では、当たった条件（例 {@code SUSPENDED+NO_ADMIN+PASSWORD}）を {@code rejection_kind} の列に入れる。列の名前
 * （受け付けなかった投入の理由の種類）と使い方がずれるが、結果が成功の行に失敗の理由（{@code failure_reason}）を入れないため、また列を
 * 足さないため（依頼者の決定 D1: A）。列の使い方は出来事の種類ごとに読む。
 *
 * <p>ロールとグループの操作の出来事（Intent 261004-role-menu の U3・U4、契約 C10）は、対象のロール・対象のグループ・決めた型から作った
 * JSON の {@code detail}（V10 で足した NULL を許す列）を持つ。{@code detail} は Java の文字列の長さ（UTF-16 の単位）で
 * {@value #MAX_DETAIL_LENGTH} までで、超える値はファクトリーで断る（切り詰めない。U3 の BR8.6）。既存の出来事では空のまま。
 *
 * <p>パスワード・トークン・パスワードのハッシュ値・Authorization ヘッダーの項目を持たない（BR2.3、NFR3.1）。文字列化ではメールアドレスを
 * 伏せる（U1 のメソッドの呼び出しの追跡が引数・戻り値を文字列にするため）。
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    /** {@code detail} の長さの上限（Java の文字列の長さ、UTF-16 の単位。列 {@code detail} の長さと同じ。U3 の BR8.6）。 */
    public static final int MAX_DETAIL_LENGTH = 16_384;

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

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    // 列は dsl_hash。項目名に hash を使わないのは、パスワードのハッシュ値の項目を持たないことを項目名で確かめる既存のテスト
    // （AuditEventTest）に合わせるため。値は DSL の本文の識別（SHA-256）で、秘密ではない。
    @Column(name = "dsl_hash", updatable = false, length = 64)
    private String dslDigest;

    @Column(name = "dsl_source", updatable = false, length = 16)
    private String dslSource;

    @Column(name = "rejection_kind", updatable = false, length = 32)
    private String rejectionKind;

    @Column(name = "target_user_id", updatable = false)
    private Long targetUserId;

    @Column(name = "target_invitation_id", updatable = false)
    private Long targetInvitationId;

    @Column(name = "target_role_id", updatable = false)
    private Long targetRoleId;

    @Column(name = "target_group_id", updatable = false)
    private Long targetGroupId;

    @Column(name = "detail", updatable = false, length = MAX_DETAIL_LENGTH)
    private String detail;

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
     * DSL の操作の監査イベントを作る（Intent 260923-dsl-schema-loader の U4、契約 C7）。
     *
     * @param occurredAt 出来事が起きた日時
     * @param eventType 種類
     * @param result 結果
     * @param sourceIp 接続元IP
     * @param userAgent User-Agent（無ければ null）
     * @param traceId トレースID（無ければ null）
     * @param actorUserId 操作した管理者の利用者 ID
     * @param dslHash DSL の識別（無ければ null）
     * @param dslSource DSL の出どころ（無ければ null）
     * @param rejectionKind 受け付けなかった投入の理由の種類（無ければ null）
     */
    public AuditEvent(
            Instant occurredAt,
            AuditEventType eventType,
            AuditResult result,
            String sourceIp,
            String userAgent,
            String traceId,
            Long actorUserId,
            String dslHash,
            String dslSource,
            String rejectionKind) {
        this(occurredAt, eventType, result, null, null, sourceIp, userAgent, null, traceId);
        this.actorUserId = actorUserId;
        this.dslDigest = dslHash;
        this.dslSource = dslSource;
        this.rejectionKind = rejectionKind;
    }

    /**
     * 対象を持つ監査イベントを作る（Intent 260925-user-management の契約 C8。例: パスワードの変更）。メールアドレス・要求のパス・
     * DSL の項目は空にする。
     *
     * @param occurredAt 出来事が起きた日時
     * @param eventType 種類
     * @param result 結果
     * @param failureReason 失敗の理由（成功なら null）
     * @param sourceIp 接続元IP
     * @param userAgent User-Agent（無ければ null）
     * @param traceId トレースID（無ければ null）
     * @param actorUserId 操作した人の利用者 ID（無ければ null）
     * @param targetUserId 対象の利用者 ID（無ければ null）
     * @param targetInvitationId 対象の招待の ID（無ければ null）
     * @return 監査イベント
     */
    public static AuditEvent withTarget(
            Instant occurredAt,
            AuditEventType eventType,
            AuditResult result,
            AuditFailureReason failureReason,
            String sourceIp,
            String userAgent,
            String traceId,
            Long actorUserId,
            Long targetUserId,
            Long targetInvitationId) {
        AuditEvent event =
                new AuditEvent(occurredAt, eventType, result, null, failureReason, sourceIp, userAgent, null, traceId);
        event.actorUserId = actorUserId;
        event.targetUserId = targetUserId;
        event.targetInvitationId = targetInvitationId;
        return event;
    }

    /**
     * ロール・グループを対象に持つ監査イベントを作る（Intent 261004-role-menu の U3・U4、契約 C10、U3 の BR8.4〜BR8.6）。メールアドレス・
     * 要求のパス・DSL の項目・対象の招待は空にする。
     *
     * <p>{@code detail} は呼び出し元が決めた型から作った JSON の文字列で、Java の文字列の長さ（UTF-16 の単位）で
     * {@value #MAX_DETAIL_LENGTH} を超えたら作らない（切り詰めない。超えそうな中身は呼び出し元が要約にしてから渡す）。例外の文には
     * 長さだけを載せ、中身を載せない。
     *
     * @param occurredAt 出来事が起きた日時
     * @param eventType 種類
     * @param result 結果
     * @param failureReason 失敗の理由（成功なら null）
     * @param sourceIp 接続元IP
     * @param userAgent User-Agent（無ければ null）
     * @param traceId トレースID（無ければ null）
     * @param actorUserId 操作した人の利用者 ID（無ければ null）
     * @param targetUserId 対象の利用者 ID（無ければ null）
     * @param targetRoleId 対象のロールの ID（無ければ null）
     * @param targetGroupId 対象のグループの ID（無ければ null）
     * @param detail 決めた型から作った JSON の文字列（無ければ null）
     * @return 監査イベント
     * @throws IllegalArgumentException {@code detail} が上限を超えるとき
     */
    public static AuditEvent withRoleGroupTarget(
            Instant occurredAt,
            AuditEventType eventType,
            AuditResult result,
            AuditFailureReason failureReason,
            String sourceIp,
            String userAgent,
            String traceId,
            Long actorUserId,
            Long targetUserId,
            Long targetRoleId,
            Long targetGroupId,
            String detail) {
        if (detail != null && detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "detail が上限を超えています: length=" + detail.length() + ", max=" + MAX_DETAIL_LENGTH);
        }
        AuditEvent event =
                new AuditEvent(occurredAt, eventType, result, null, failureReason, sourceIp, userAgent, null, traceId);
        event.actorUserId = actorUserId;
        event.targetUserId = targetUserId;
        event.targetRoleId = targetRoleId;
        event.targetGroupId = targetGroupId;
        event.detail = detail;
        return event;
    }

    /**
     * 起動時の出来事（要求を持たない出来事）の監査イベントを作る（Intent 261004-safety-carryover の FR1.5・FR1.6・FR1.6a）。
     *
     * <p>結果は成功。操作した人・メールアドレス・失敗の理由・User-Agent・要求のパス・トレースID・DSL の項目・対象の招待は空にする。
     * {@code rejectionKind} には救済で当たった条件を入れる（作成では null。列の使い方はクラスの説明のとおり）。
     *
     * @param occurredAt 出来事が起きた日時
     * @param eventType 種類
     * @param sourceIp 接続元（要求が無いため決まった値）
     * @param targetUserId 対象の利用者 ID
     * @param rejectionKind 救済で当たった条件（無ければ null）
     * @return 監査イベント
     */
    public static AuditEvent ofStartup(
            Instant occurredAt, AuditEventType eventType, String sourceIp, long targetUserId, String rejectionKind) {
        AuditEvent event =
                new AuditEvent(occurredAt, eventType, AuditResult.SUCCESS, null, null, sourceIp, null, null, null);
        event.targetUserId = targetUserId;
        event.rejectionKind = rejectionKind;
        return event;
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

    /**
     * 操作した管理者の利用者 ID を返す。
     *
     * @return 利用者 ID（DSL の操作・パスワードの変更のとき以外は null）
     */
    public Long getActorUserId() {
        return actorUserId;
    }

    /**
     * DSL の識別を返す。
     *
     * @return DSL の識別（無ければ null）
     */
    public String getDslHash() {
        return dslDigest;
    }

    /**
     * DSL の出どころを返す。
     *
     * @return 出どころ（無ければ null）
     */
    public String getDslSource() {
        return dslSource;
    }

    /**
     * 受け付けなかった投入の理由の種類を返す。
     *
     * @return 理由の種類（無ければ null）
     */
    public String getRejectionKind() {
        return rejectionKind;
    }

    /**
     * 対象の利用者 ID を返す。
     *
     * @return 利用者 ID（対象の利用者を持つ出来事のとき以外は null）
     */
    public Long getTargetUserId() {
        return targetUserId;
    }

    /**
     * 対象の招待の ID を返す。
     *
     * @return 招待の ID（対象の招待を持つ出来事のとき以外は null）
     */
    public Long getTargetInvitationId() {
        return targetInvitationId;
    }

    /** メールアドレスを伏せて文字列にする（アプリのログにメールアドレスを出さないため）。 */
    /**
     * 対象のロールの ID を返す。
     *
     * @return ロールの ID（対象のロールを持つ出来事のとき以外は null）
     */
    public Long getTargetRoleId() {
        return targetRoleId;
    }

    /**
     * 対象のグループの ID を返す。
     *
     * @return グループの ID（対象のグループを持つ出来事のとき以外は null）
     */
    public Long getTargetGroupId() {
        return targetGroupId;
    }

    /**
     * 決めた型から作った JSON の文字列を返す。
     *
     * @return JSON の文字列（無ければ null）
     */
    public String getDetail() {
        return detail;
    }

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
                + ", actorUserId=" + actorUserId
                + ", dslHash=" + dslDigest
                + ", dslSource=" + dslSource
                + ", rejectionKind=" + rejectionKind
                + ", targetUserId=" + targetUserId
                + ", targetInvitationId=" + targetInvitationId
                + ", targetRoleId=" + targetRoleId
                + ", targetGroupId=" + targetGroupId
                // detail の中身は出さず長さだけにする（TRACE の行が上限まで膨らむのを避ける。計画の D-10）。
                + ", detailLength=" + (detail == null ? "null" : String.valueOf(detail.length()))
                + "]";
    }
}
