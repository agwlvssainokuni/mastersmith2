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
package cherry.mastersmith.businessdata.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 業務データの操作の出来事（アプリの中の知らせ、保存しない。Intent 261016-business-data-views の U2、契約 C5、BR11、entities.md の
 * BusinessDataChangedEvent）。書き込みの口から戻った後（対象DB の接続を返した後）か、⑥ で拒んだ直後に業務処理の層が出し、
 * {@code audit} が確定の後の受け取り（トランザクションが無いときも受ける）で監査の行に写す。
 *
 * <p>主キー以外の値・更新の前後の値・JDBC の例外のメッセージ・接続先を持たない。文字列にするときは主キー（{@code detail}）を伏せる。
 *
 * @param actorUserId 操作した人の利用者 ID（要求の文脈から読んだ値）
 * @param workRoleId 作業ロールの ID（無ければ null）
 * @param schema スキーマ名（写しの DSL の名前）
 * @param table テーブル名（写しの DSL の名前）
 * @param operation 操作
 * @param result 結果
 * @param reason 監査の理由の値（成功のときは null）
 * @param detail 主キーの JSON（{@link AuditDetailBuilder} が作ったもの。主キーの無いテーブルの作成・主キーの決まらない失敗では null）
 * @param keyColumnCount 主キーのカラムの数
 * @param keyTruncated 主キーの値を切り詰めたなら true
 * @param occurredAt 出来事が起きた日時（注入した時計の値）
 * @param origin 要求の送り手の情報
 */
public record BusinessDataChangedEvent(
        long actorUserId,
        Long workRoleId,
        String schema,
        String table,
        WriteOperation operation,
        BusinessDataAuditResult result,
        BusinessDataAuditReason reason,
        String detail,
        int keyColumnCount,
        boolean keyTruncated,
        Instant occurredAt,
        WriteOrigin origin) {

    /** 必須の値と、結果と理由の組み合わせを確かめる。 */
    public BusinessDataChangedEvent {
        Objects.requireNonNull(schema, "schema は必須です");
        Objects.requireNonNull(table, "table は必須です");
        Objects.requireNonNull(operation, "operation は必須です");
        Objects.requireNonNull(result, "result は必須です");
        Objects.requireNonNull(occurredAt, "occurredAt は必須です");
        Objects.requireNonNull(origin, "origin は必須です");
        if ((result == BusinessDataAuditResult.SUCCESS) != (reason == null)) {
            throw new IllegalArgumentException("成功は理由なし、失敗は理由ありです");
        }
        if (result == BusinessDataAuditResult.UNKNOWN && reason != BusinessDataAuditReason.BUSINESS_TARGET_DB_TIMEOUT) {
            throw new IllegalArgumentException("確定したか分からない結果は、時間切れだけです");
        }
    }

    /**
     * 出来事を作る。
     *
     * @param actorUserId 操作した人の利用者 ID
     * @param workRoleId 作業ロールの ID（無ければ null）
     * @param schema スキーマ名
     * @param table テーブル名
     * @param operation 操作
     * @param reason 監査の理由の値（成功のときは null）
     * @param detail 主キーの detail
     * @param occurredAt 日時
     * @param origin 要求の送り手の情報
     * @return 出来事
     */
    public static BusinessDataChangedEvent of(
            long actorUserId,
            Long workRoleId,
            String schema,
            String table,
            WriteOperation operation,
            BusinessDataAuditReason reason,
            AuditDetailBuilder.AuditDetail detail,
            Instant occurredAt,
            WriteOrigin origin) {
        return new BusinessDataChangedEvent(
                actorUserId,
                workRoleId,
                schema,
                table,
                operation,
                reason == null ? BusinessDataAuditResult.SUCCESS : BusinessDataAuditResult.failureOf(reason),
                reason,
                detail.json(),
                detail.keyColumnCount(),
                detail.keyTruncated(),
                occurredAt,
                origin);
    }

    /** 主キー（detail）を伏せる。 */
    @Override
    public String toString() {
        return "BusinessDataChangedEvent[schema=" + schema + ", table=" + table + ", operation=" + operation
                + ", result=" + result + ", reason=" + reason + ", keyColumns=" + keyColumnCount + ", keyTruncated="
                + keyTruncated + "]";
    }
}
