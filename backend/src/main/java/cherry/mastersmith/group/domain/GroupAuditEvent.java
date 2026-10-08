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
package cherry.mastersmith.group.domain;

import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import java.util.Objects;

/**
 * グループの操作の監査の出来事（アプリの中の知らせ、保存しない。契約 C10、BR8.1〜BR8.5・BR8.7）。業務の判定に届いた操作の1回につき1件。
 *
 * <p>業務処理がトランザクションの中で知らせ、確定の後に AuditLog（{@code audit}）が受けて監査の行を1行追記する。成功は操作の
 * トランザクションの中、業務の拒否は1つ目のトランザクションを巻き戻した後の2つ目のトランザクション（書き込みなし）の中で知らせる
 * （BR5.7、{@code reliability-design.md} 2.3）。入力の誤り・排他の待ちの上限切れ・読み取りでは出さない（BR8.3）。
 *
 * <p>メールアドレス・氏名・パスワード・トークン・ハッシュ値を持たない（BR8.7）。対象の利用者は ID だけで示す。
 *
 * @param operation 操作の区分
 * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値）
 * @param targetGroupId 対象のグループの ID（作成の成功では作った ID、作成の名前の重なりでは null、存在しない ID の拒否では要求の ID。
 *     BR8.4）
 * @param targetUserId 対象の利用者 ID（メンバーの追加・外しだけ。存在しない利用者の拒否でも要求の ID。ほかの操作では null）
 * @param succeeded 成功したか
 * @param failure 失敗の理由（成功のときは null）
 * @param detail detail の中身（グループ・利用者が無い拒否では null。BR8.5）
 * @param occurredAt 出来事が起きた日時（注入した時計の値）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record GroupAuditEvent(
        GroupOperation operation,
        long actorUserId,
        Long targetGroupId,
        Long targetUserId,
        boolean succeeded,
        GroupAuditFailure failure,
        GroupAuditDetail detail,
        Instant occurredAt,
        String sourceIp,
        String userAgent,
        String traceId) {

    /** 必須の値と、成否と理由・対象・detail の組み合わせを確かめる。 */
    public GroupAuditEvent {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceIp, "sourceIp");
        if (succeeded != (failure == null)) {
            throw new IllegalArgumentException("成功は理由なし、失敗は理由ありです");
        }
        boolean memberOperation = operation == GroupOperation.ADD_MEMBER || operation == GroupOperation.REMOVE_MEMBER;
        if (memberOperation != (targetUserId != null)) {
            throw new IllegalArgumentException("対象の利用者はメンバーの追加・外しだけが持ちます");
        }
        if (succeeded && targetGroupId == null) {
            throw new IllegalArgumentException("成功の出来事は対象のグループを持ちます");
        }
        boolean missingTarget =
                failure == GroupAuditFailure.GROUP_NOT_FOUND || failure == GroupAuditFailure.USER_NOT_FOUND;
        if (missingTarget && detail != null) {
            throw new IllegalArgumentException("グループ・利用者が無い拒否は detail を持ちません");
        }
        if (!missingTarget && detail == null) {
            throw new IllegalArgumentException("detail が必要です");
        }
    }

    /**
     * 成功の出来事を作る。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した管理者の利用者 ID
     * @param targetGroupId 対象のグループの ID
     * @param targetUserId 対象の利用者 ID（メンバーの追加・外しだけ。ほかは null）
     * @param detail detail の中身
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static GroupAuditEvent succeeded(
            GroupOperation operation,
            long actorUserId,
            long targetGroupId,
            Long targetUserId,
            GroupAuditDetail detail,
            Instant occurredAt,
            RequestOrigin origin) {
        return new GroupAuditEvent(
                operation,
                actorUserId,
                targetGroupId,
                targetUserId,
                true,
                null,
                detail,
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }

    /**
     * 業務の拒否の出来事を作る。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した管理者の利用者 ID
     * @param targetGroupId 対象のグループの ID（作成の名前の重なりでは null）
     * @param targetUserId 対象の利用者 ID（メンバーの追加・外しだけ。ほかは null）
     * @param failure 失敗の理由
     * @param detail detail の中身（グループ・利用者が無い拒否では null）
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static GroupAuditEvent failed(
            GroupOperation operation,
            long actorUserId,
            Long targetGroupId,
            Long targetUserId,
            GroupAuditFailure failure,
            GroupAuditDetail detail,
            Instant occurredAt,
            RequestOrigin origin) {
        Objects.requireNonNull(failure, "failure");
        return new GroupAuditEvent(
                operation,
                actorUserId,
                targetGroupId,
                targetUserId,
                false,
                failure,
                detail,
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }
}
