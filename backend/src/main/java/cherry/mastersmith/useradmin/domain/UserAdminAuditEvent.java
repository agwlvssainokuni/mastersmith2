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
package cherry.mastersmith.useradmin.domain;

import cherry.mastersmith.user.domain.RequestOrigin;
import java.time.Instant;
import java.util.Objects;

/**
 * 管理の操作の監査の出来事（アプリの中の知らせ、保存しない。契約 C6、BR6.1〜BR6.3）。業務の判定に届いた操作の1回につき1件。
 *
 * <p>業務処理のトランザクションの中で知らせ、確定の後に AuditLog（{@code audit}）が受けて監査の行を1行追記する。排他の待ちの上限切れ・
 * 入力の誤り・認可の入口の拒否では出さない（BR6.4）。メールアドレス・氏名・検索の文字・失敗回数・トークンを持たない（BR6.2）。
 *
 * @param operation 操作の区分
 * @param actorUserId 操作した管理者の利用者 ID（認証の主体から読んだ値。BR2.8）
 * @param targetUserId 要求の利用者 ID（いない ID のまま。BR6.2）
 * @param succeeded 成功したか
 * @param failure 失敗の理由（成功のときは null）
 * @param occurredAt 出来事が起きた日時（注入した時計の値）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切った値。無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record UserAdminAuditEvent(
        AdminOperation operation,
        long actorUserId,
        long targetUserId,
        boolean succeeded,
        UserAdminAuditFailure failure,
        Instant occurredAt,
        String sourceIp,
        String userAgent,
        String traceId) {

    /** 必須の値と、成否と理由の組み合わせを確かめる（成功は理由なし、失敗は理由あり）。 */
    public UserAdminAuditEvent {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceIp, "sourceIp");
        if (succeeded != (failure == null)) {
            throw new IllegalArgumentException("成功は理由なし、失敗は理由ありです");
        }
    }

    /**
     * 成功の出来事を作る。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した管理者の利用者 ID
     * @param targetUserId 要求の利用者 ID
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static UserAdminAuditEvent succeeded(
            AdminOperation operation, long actorUserId, long targetUserId, Instant occurredAt, RequestOrigin origin) {
        return new UserAdminAuditEvent(
                operation,
                actorUserId,
                targetUserId,
                true,
                null,
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }

    /**
     * 失敗の出来事を作る。
     *
     * @param operation 操作の区分
     * @param actorUserId 操作した管理者の利用者 ID
     * @param targetUserId 要求の利用者 ID
     * @param failure 失敗の理由
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static UserAdminAuditEvent failed(
            AdminOperation operation,
            long actorUserId,
            long targetUserId,
            UserAdminAuditFailure failure,
            Instant occurredAt,
            RequestOrigin origin) {
        Objects.requireNonNull(failure, "failure");
        return new UserAdminAuditEvent(
                operation,
                actorUserId,
                targetUserId,
                false,
                failure,
                occurredAt,
                origin.sourceIp(),
                origin.userAgent(),
                origin.traceId());
    }
}
