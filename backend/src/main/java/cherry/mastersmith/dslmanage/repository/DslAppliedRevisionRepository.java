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
package cherry.mastersmith.dslmanage.repository;

import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslAppliedRevisionRecord;
import cherry.mastersmith.dslmanage.domain.DslContent;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 適用の履歴の表（{@code dsl_applied_revisions}）の DB アクセス。呼び出し側（業務処理）のトランザクションの中で使う。
 *
 * <p>適用は本文を読み直さず、プレビューの行を1文で履歴へ写す（NFR 設計の performance-design.md 3節）。今の状態と履歴の一覧は本文の
 * 列を取らない。適用中の DSL は、適用した日時が最も新しい1件（同じ日時なら追加の順。{@code entities.md}）。
 */
@Repository
public class DslAppliedRevisionRepository {

    private static final String COPY_SQL = "INSERT INTO dsl_applied_revisions"
            + " (revision_id, yaml_bytes, dsl_hash, source, applied_by_user_id, applied_at)"
            + " SELECT CAST(?1 AS UUID), p.yaml_bytes, p.dsl_hash, p.source, CAST(?3 AS BIGINT),"
            + " CAST(?4 AS TIMESTAMP WITH TIME ZONE)"
            + " FROM dsl_previews p WHERE p.preview_id = CAST(?2 AS UUID)";

    private static final String REF_SELECT = "select new cherry.mastersmith.dslmanage.domain.DslAppliedRef("
            + "r.revisionId, r.dslHash, r.source, r.appliedByUserId, r.appliedAt) from DslAppliedRevisionRecord r";

    private static final String CURRENT_ORDER = " order by r.appliedAt desc, r.sequenceNo desc";

    private final EntityManager entityManager;

    /**
     * DB アクセスを作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public DslAppliedRevisionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * previewId のプレビューの行を、履歴に1行写す（本文を読み直さない。BR4.2 の (1)）。
     *
     * @param revisionId 新しい版の識別
     * @param previewId 写すプレビューの識別
     * @param appliedByUserId 適用した管理者の利用者 ID
     * @param appliedAt 適用した日時
     * @return 足した行の数（プレビューが一致すれば 1、無ければ 0）
     */
    public int copyFromPreview(UUID revisionId, UUID previewId, long appliedByUserId, Instant appliedAt) {
        return entityManager
                .createNativeQuery(COPY_SQL)
                .setParameter(1, revisionId)
                .setParameter(2, previewId)
                .setParameter(3, appliedByUserId)
                .setParameter(4, appliedAt)
                .executeUpdate();
    }

    /**
     * 適用中の版の参照を、本文を読まずに返す。
     *
     * @return 参照（履歴が無ければ空）
     */
    public Optional<DslAppliedRef> findCurrentRef() {
        List<DslAppliedRef> rows = entityManager
                .createQuery(REF_SELECT + CURRENT_ORDER, DslAppliedRef.class)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }

    /**
     * 履歴の参照を、追加の順の新しい順に、本文を読まずに返す（BR6.1）。
     *
     * @return 参照の一覧
     */
    public List<DslAppliedRef> findRefsNewestFirst() {
        return entityManager
                .createQuery(REF_SELECT + " order by r.sequenceNo desc", DslAppliedRef.class)
                .getResultList();
    }

    /**
     * 適用中の版の本文と参照を返す（起動時の読み込み。BR5.1）。
     *
     * @return 本文と参照（履歴が無ければ空）
     */
    public Optional<DslContent<DslAppliedRef>> findCurrentContent() {
        List<DslAppliedRevisionRecord> rows = entityManager
                .createQuery("select r from DslAppliedRevisionRecord r" + CURRENT_ORDER, DslAppliedRevisionRecord.class)
                .setMaxResults(1)
                .getResultList();
        return detach(rows);
    }

    /**
     * 版の識別を指定して、本文と参照を返す。
     *
     * @param revisionId 版の識別
     * @return 本文と参照（無ければ空）
     */
    public Optional<DslContent<DslAppliedRef>> findContent(UUID revisionId) {
        List<DslAppliedRevisionRecord> rows = entityManager
                .createQuery(
                        "select r from DslAppliedRevisionRecord r where r.revisionId = :revisionId",
                        DslAppliedRevisionRecord.class)
                .setParameter("revisionId", revisionId)
                .getResultList();
        return detach(rows);
    }

    /**
     * 追加の順の新しいものから {@code keep} 件を残し、それより古い行を消す（BR4.4）。
     *
     * @param keep 残す件数（1 以上）
     * @return 消した行の数
     */
    public int deleteOlderThanNewest(int keep) {
        if (keep < 1) {
            throw new IllegalArgumentException("残す件数は 1 以上にしてください: " + keep);
        }
        List<Long> boundary = entityManager
                .createQuery(
                        "select r.sequenceNo from DslAppliedRevisionRecord r order by r.sequenceNo desc", Long.class)
                .setFirstResult(keep)
                .setMaxResults(1)
                .getResultList();
        if (boundary.isEmpty()) {
            return 0;
        }
        return entityManager
                .createQuery("delete from DslAppliedRevisionRecord r where r.sequenceNo <= :boundary")
                .setParameter("boundary", boundary.getFirst())
                .executeUpdate();
    }

    /**
     * 履歴の件数を返す。
     *
     * @return 件数
     */
    public long count() {
        return entityManager
                .createQuery("select count(r) from DslAppliedRevisionRecord r", Long.class)
                .getSingleResult();
    }

    private Optional<DslContent<DslAppliedRef>> detach(List<DslAppliedRevisionRecord> rows) {
        Optional<DslContent<DslAppliedRef>> content =
                rows.stream().findFirst().map(DslAppliedRevisionRecord::toContent);
        // 本文（最大 10MB）をトランザクションの終わりまで持ち続けない。
        entityManager.clear();
        return content;
    }
}
