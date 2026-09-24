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
package cherry.mastersmith.dslmanage.testsupport;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** 監査イベントの表の DSL の操作の行（V6 の列を含む）を読むテストの補助。 */
public final class DslAuditRows {

    private final JdbcTemplate jdbc;

    /**
     * 作る。
     *
     * @param jdbc 内部DBへの問い合わせ
     */
    public DslAuditRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * DSL の操作の監査の1行。
     *
     * @param eventType 種類
     * @param result 結果
     * @param actorUserId 操作した管理者
     * @param dslHash DSL の識別
     * @param dslSource 出どころ
     * @param rejectionKind 理由の種類
     * @param sourceIp 接続元IP
     * @param occurredAt 日時（文字列）
     * @param all 行のすべての列をつないだ文字列（含まれないことの確かめに使う）
     */
    public record Row(
            String eventType,
            String result,
            Long actorUserId,
            String dslHash,
            String dslSource,
            String rejectionKind,
            String sourceIp,
            String occurredAt,
            String all) {}

    /**
     * DSL の操作の行を追記の順に返す。
     *
     * @return 行の一覧
     */
    public List<Row> dslRows() {
        return jdbc.query(
                "SELECT * FROM audit_events WHERE event_type LIKE 'DSL_%' ORDER BY audit_event_id",
                (rs, rowNum) -> {
                    StringBuilder all = new StringBuilder();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        all.append(rs.getString(i)).append('|');
                    }
                    long actor = rs.getLong("actor_user_id");
                    return new Row(
                            rs.getString("event_type"),
                            rs.getString("result"),
                            rs.wasNull() ? null : actor,
                            rs.getString("dsl_hash"),
                            rs.getString("dsl_source"),
                            rs.getString("rejection_kind"),
                            rs.getString("source_ip"),
                            rs.getString("occurred_at"),
                            all.toString());
                });
    }

    /**
     * 最後の DSL の操作の行を返す。
     *
     * @return 行
     */
    public Row last() {
        List<Row> rows = dslRows();
        if (rows.isEmpty()) {
            throw new IllegalStateException("DSL の操作の監査の行がありません");
        }
        return rows.getLast();
    }

    /**
     * 監査の表の行の数を返す。
     *
     * @return 行の数
     */
    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }
}
