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
package cherry.mastersmith.audit.testsupport;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 監査イベントの表（{@code audit_events}）の行を読むテストの補助（計画の C8）。
 *
 * <p>監査ログを見る API を本Intentでは作らないため、結合テストは {@code JdbcTemplate} で表を直接読む。テストのためだけの
 * API・操作を本番のコードに足さないための仕組みである。
 */
public final class AuditRows {

    private static final String SELECT =
            "SELECT audit_event_id, occurred_at, event_type, result, entered_email, failure_reason,"
                    + " source_ip, user_agent, request_path, trace_id FROM audit_events";

    private static final RowMapper<AuditRow> MAPPER = AuditRows::map;

    private final JdbcTemplate jdbc;

    /**
     * 作る。
     *
     * @param jdbc 内部DBへの問い合わせ
     */
    public AuditRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 監査イベントの表の1行。
     *
     * @param auditEventId 監査イベントの ID
     * @param occurredAt 発生の日時
     * @param eventType 種類
     * @param result 結果
     * @param enteredEmail 入力されたメールアドレス
     * @param failureReason 失敗の理由
     * @param sourceIp 接続元IP
     * @param userAgent User-Agent
     * @param requestPath 要求のパス
     * @param traceId トレースID
     */
    public record AuditRow(
            long auditEventId,
            Instant occurredAt,
            String eventType,
            String result,
            String enteredEmail,
            String failureReason,
            String sourceIp,
            String userAgent,
            String requestPath,
            String traceId) {}

    /**
     * 行の数を返す。
     *
     * @return 行の数
     */
    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 発生の日時、同じ日時なら ID の順に、すべての行を返す。
     *
     * @return 行の一覧
     */
    public List<AuditRow> all() {
        return jdbc.query(SELECT + " ORDER BY occurred_at, audit_event_id", MAPPER);
    }

    /**
     * 最後に追記された行（ID が最大の行）を返す。
     *
     * @return 最後の行
     */
    public AuditRow last() {
        List<AuditRow> rows = jdbc.query(SELECT + " ORDER BY audit_event_id DESC FETCH FIRST 1 ROWS ONLY", MAPPER);
        if (rows.isEmpty()) {
            throw new IllegalStateException("監査イベントの行がありません");
        }
        return rows.getFirst();
    }

    private static AuditRow map(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime occurredAt = rs.getObject("occurred_at", OffsetDateTime.class);
        return new AuditRow(
                rs.getLong("audit_event_id"),
                occurredAt == null ? null : occurredAt.toInstant(),
                rs.getString("event_type"),
                rs.getString("result"),
                rs.getString("entered_email"),
                rs.getString("failure_reason"),
                rs.getString("source_ip"),
                rs.getString("user_agent"),
                rs.getString("request_path"),
                rs.getString("trace_id"));
    }
}
