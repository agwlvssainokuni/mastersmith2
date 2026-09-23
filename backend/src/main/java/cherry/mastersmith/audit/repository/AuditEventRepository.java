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
package cherry.mastersmith.audit.repository;

import cherry.mastersmith.audit.domain.AuditEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * 監査イベントの表の DB アクセス（BR4.1、NFR1.3、NFR3.2）。
 *
 * <p>Spring Data の既定の部品（{@code JpaRepository} など、削除をまとめて持つもの）は継承せず、最小の {@link Repository} から
 * <strong>追記と読み取りだけ</strong>を宣言する。更新・削除の操作と、表を変える問い合わせ（{@code @Modifying}）は置かない。
 *
 * <p>追記は1件につき挿入1回だけで、ほかの表を読まない。主キーは DB の連番で、挿入の前に読み取りをしない
 * （{@code nfr-design/performance-design.md} 1章）。
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    /**
     * 監査イベントを1件追記する。
     *
     * @param <S> 監査イベントの型
     * @param auditEvent 監査イベント（ID を持たない新しい行）
     * @return 追記した監査イベント（ID が入る）
     */
    <S extends AuditEvent> S save(S auditEvent);

    /**
     * ID で監査イベントを読む。
     *
     * @param auditEventId 監査イベントの ID
     * @return 監査イベント（無ければ空）
     */
    Optional<AuditEvent> findById(Long auditEventId);

    /**
     * 発生の日時の順に監査イベントを読む。
     *
     * @return 監査イベントの一覧
     */
    List<AuditEvent> findAllByOrderByOccurredAtAsc();
}
