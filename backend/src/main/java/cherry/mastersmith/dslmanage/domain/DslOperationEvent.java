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
package cherry.mastersmith.dslmanage.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * DSL の操作の出来事（契約 C7。AuditLog が元の操作の確定の後に受けて監査の表に記録する）。
 *
 * <p>DSL の本文と対象DB の接続先を持たない（BR7.2）。契約 C7 の項目に、監査の表の必須の列（接続元IP）と User-Agent を足した
 * （項目の追加は安全な変更。契約の決まり）。
 *
 * @param type 種類
 * @param actorUserId 操作した管理者の利用者 ID
 * @param occurredAt 日時（UTC の時計から得た時点）
 * @param dslHash DSL の識別（読む前に大きさで断った投入では null）
 * @param source 出どころ（無ければ null）
 * @param rejectionKind 受け付けなかった投入の理由の種類（最初の誤りの種類、または {@code SIZE_LIMIT}。ほかは null。BR7.4）
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record DslOperationEvent(
        DslOperationType type,
        long actorUserId,
        Instant occurredAt,
        String dslHash,
        DslSource source,
        String rejectionKind,
        String sourceIp,
        String userAgent,
        String traceId) {

    /** 受け付けなかった投入の、大きさの上限の理由の種類。 */
    public static final String SIZE_LIMIT = "SIZE_LIMIT";

    /** 必須の値と、理由の種類の有無を確かめる。 */
    public DslOperationEvent {
        Objects.requireNonNull(type, "type は必須です");
        Objects.requireNonNull(occurredAt, "occurredAt は必須です");
        Objects.requireNonNull(sourceIp, "sourceIp は必須です");
        if ((type == DslOperationType.DSL_SUBMISSION_REJECTED) != (rejectionKind != null)) {
            throw new IllegalArgumentException("rejectionKind は受け付けなかった投入のときだけ持ちます");
        }
    }
}
