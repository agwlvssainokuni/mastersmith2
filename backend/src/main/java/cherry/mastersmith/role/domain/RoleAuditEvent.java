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
package cherry.mastersmith.role.domain;

import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import java.util.Objects;

/**
 * ロールの操作の監査の出来事（アプリの中の知らせ、保存しない。契約 C10、BR11.1〜BR11.4・BR11.8）。業務の判定に届いた変える操作の
 * 1回につき1件。
 *
 * <p>業務処理がトランザクションの中で知らせ、確定の後に AuditLog（{@code audit}）が受けて監査の行を1行追記する。成功は操作の
 * トランザクションの中、業務の拒否と違反の読み替えは1つ目のトランザクションを巻き戻した後の2つ目のトランザクション（書き込みなし）の
 * 中で知らせる（計画の 4.3、13節 Q2: A）。入力の誤り・排他の待ちの上限切れ・読み取りでは出さない（BR11.3）。
 *
 * <p>メールアドレス・氏名・パスワード・トークン・ハッシュ値を持たない（BR11.8）。対象の利用者・グループは ID だけで示す（B5 の割り当てと
 * 外しで使う。作業ロールの切り替えは対象の利用者に操作した人と同じ ID を入れる。BR11.4。B4 の操作では null）。
 *
 * @param operation 操作の区分
 * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値）
 * @param targetRoleId 対象のロールの ID（作成の成功では作った ID、作成の名前の重なりでは null、存在しない ID の拒否では要求の ID）
 * @param targetUserId 対象の利用者 ID（利用者への割り当て・外しと作業ロールの切り替え。ほかは null）
 * @param targetGroupId 対象のグループの ID（グループへの割り当て・外し。ほかは null）
 * @param succeeded 成功したか
 * @param failure 失敗の理由（成功のときは null）
 * @param detail detail の中身（ロールが無い拒否と、割り当ての外のロールへの切り替えの拒否では null）
 * @param occurredAt 出来事が起きた日時（注入した時計の値）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record RoleAuditEvent(
        RoleOperation operation,
        long actorUserId,
        Long targetRoleId,
        Long targetUserId,
        Long targetGroupId,
        boolean succeeded,
        RoleAuditFailure failure,
        RoleAuditDetail detail,
        Instant occurredAt,
        String sourceIp,
        String userAgent,
        String traceId) {

    /** 必須の値と、成否と理由・対象・detail の組み合わせを確かめる。 */
    public RoleAuditEvent {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceIp, "sourceIp");
        if (succeeded != (failure == null)) {
            throw new IllegalArgumentException("成功は理由なし、失敗は理由ありです");
        }
        if (succeeded && targetRoleId == null) {
            throw new IllegalArgumentException("成功の出来事は対象のロールを持ちます");
        }
        boolean missingTarget =
                failure == RoleAuditFailure.ROLE_NOT_FOUND || failure == RoleAuditFailure.ROLE_NOT_ASSIGNED;
        if (missingTarget && detail != null) {
            throw new IllegalArgumentException("ロールが無い拒否・割り当ての外のロールへの切り替えの拒否は detail を持ちません");
        }
        if (!missingTarget && detail == null) {
            throw new IllegalArgumentException("detail が必要です");
        }
    }

    /**
     * 成功の出来事を作る（B4 の操作。対象の利用者・グループは null）。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した管理者の利用者 ID
     * @param targetRoleId 対象のロールの ID
     * @param detail detail の中身
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static RoleAuditEvent succeeded(
            RoleOperation operation,
            long actorUserId,
            long targetRoleId,
            RoleAuditDetail detail,
            Instant occurredAt,
            RequestOrigin origin) {
        return succeeded(operation, actorUserId, targetRoleId, null, null, detail, occurredAt, origin);
    }

    /**
     * 成功の出来事を作る（対象の利用者・グループを持つ操作。B5 の割り当て・外し・作業ロールの切り替え）。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した人の利用者 ID
     * @param targetRoleId 対象のロールの ID
     * @param targetUserId 対象の利用者 ID（無ければ null）
     * @param targetGroupId 対象のグループの ID（無ければ null）
     * @param detail detail の中身
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static RoleAuditEvent succeeded(
            RoleOperation operation,
            long actorUserId,
            long targetRoleId,
            Long targetUserId,
            Long targetGroupId,
            RoleAuditDetail detail,
            Instant occurredAt,
            RequestOrigin origin) {
        return new RoleAuditEvent(
                operation,
                actorUserId,
                targetRoleId,
                targetUserId,
                targetGroupId,
                true,
                null,
                detail,
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }

    /**
     * 業務の拒否の出来事を作る（B4 の操作。対象の利用者・グループは null）。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した管理者の利用者 ID
     * @param targetRoleId 対象のロールの ID（作成の名前の重なりでは null）
     * @param failure 失敗の理由
     * @param detail detail の中身（ロールが無い拒否と、割り当ての外のロールへの切り替えの拒否では null）
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static RoleAuditEvent failed(
            RoleOperation operation,
            long actorUserId,
            Long targetRoleId,
            RoleAuditFailure failure,
            RoleAuditDetail detail,
            Instant occurredAt,
            RequestOrigin origin) {
        return failed(operation, actorUserId, targetRoleId, null, null, failure, detail, occurredAt, origin);
    }

    /**
     * 業務の拒否の出来事を作る（対象の利用者・グループを持つ操作。B5 の割り当て・外し・作業ロールの切り替え）。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した人の利用者 ID
     * @param targetRoleId 対象のロールの ID（要求の値のまま）
     * @param targetUserId 対象の利用者 ID（要求の値のまま。無ければ null）
     * @param targetGroupId 対象のグループの ID（要求の値のまま。無ければ null）
     * @param failure 失敗の理由
     * @param detail detail の中身（ロールが無い拒否・割り当ての外のロールへの切り替えの拒否では null）
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static RoleAuditEvent failed(
            RoleOperation operation,
            long actorUserId,
            Long targetRoleId,
            Long targetUserId,
            Long targetGroupId,
            RoleAuditFailure failure,
            RoleAuditDetail detail,
            Instant occurredAt,
            RequestOrigin origin) {
        Objects.requireNonNull(failure, "failure");
        return new RoleAuditEvent(
                operation,
                actorUserId,
                targetRoleId,
                targetUserId,
                targetGroupId,
                false,
                failure,
                detail,
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }
}
