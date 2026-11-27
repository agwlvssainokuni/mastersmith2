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

import cherry.mastersmith.common.persistence.KeyedUpsert;
import cherry.mastersmith.common.persistence.KeyedUpsertExecutor;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslPreviewRecord;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslSource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * プレビューの表（{@code dsl_previews}、最大1行）の DB アクセス。呼び出し側（業務処理）のトランザクションの中で使う。
 *
 * <p>置くのは固定の鍵の1行への「あれば更新・無ければ挿入」（{@link KeyedUpsertExecutor}）で、2人がほぼ同時に置いたときは後に確定した方が残る（BR1.4、NFR8.3）。今の状態の読み取りは
 * 本文の列を取らない（NFR 設計の performance-design.md 3節）。
 */
@Repository
public class DslPreviewRepository {

    private final EntityManager entityManager;

    private final KeyedUpsertExecutor upsertExecutor;

    /**
     * DB アクセスを作る。
     *
     * @param entityManager JPA のエンティティの管理
     */
    public DslPreviewRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.upsertExecutor = new KeyedUpsertExecutor(entityManager);
    }

    /**
     * プレビューを置く（行が無ければ入れ、あれば置き換える。共通の「あれば更新・無ければ挿入」の手順）。
     *
     * @param previewId 新しいプレビューの識別
     * @param yamlBytes 本文のバイト列
     * @param dslHash DSL の識別
     * @param source 出どころ
     * @param placedByUserId 置いた管理者の利用者 ID
     * @param placedAt 置いた日時
     * @return 書いた行の数（1）
     */
    public int place(
            UUID previewId, byte[] yamlBytes, String dslHash, DslSource source, long placedByUserId, Instant placedAt) {
        // 本文のバイト列は複製せずにそのまま束縛する。どの経路で書いたかは呼び出し元に要らないため読み捨てる。
        Object[] arguments = {
            previewId,
            yamlBytes,
            dslHash,
            source.name(),
            placedByUserId,
            OffsetDateTime.ofInstant(placedAt, ZoneOffset.UTC)
        };
        upsertExecutor.upsert(KeyedUpsert.DSL_PREVIEWS, arguments, arguments);
        return 1;
    }

    /**
     * 今のプレビューの参照を、本文を読まずに返す。
     *
     * @return 参照（プレビューが無ければ空）
     */
    public Optional<DslPreviewRef> findRef() {
        List<DslPreviewRef> rows = entityManager
                .createQuery(
                        "select new cherry.mastersmith.dslmanage.domain.DslPreviewRef("
                                + "p.previewId, p.dslHash, p.source, p.placedByUserId, p.placedAt)"
                                + " from DslPreviewRecord p",
                        DslPreviewRef.class)
                .getResultList();
        return rows.stream().findFirst();
    }

    /**
     * 今のプレビューの本文と参照を返す。
     *
     * @return 本文と参照（プレビューが無ければ空）
     */
    public Optional<DslContent<DslPreviewRef>> findContent() {
        List<DslPreviewRecord> rows = entityManager
                .createQuery("select p from DslPreviewRecord p", DslPreviewRecord.class)
                .getResultList();
        Optional<DslContent<DslPreviewRef>> content = rows.stream().findFirst().map(DslPreviewRecord::toContent);
        // 本文（最大 10MB）をトランザクションの終わりまで持ち続けない。
        entityManager.clear();
        return content;
    }

    /**
     * previewId を指定してプレビューの行を消す。
     *
     * @param previewId プレビューの識別
     * @return 消した行の数（一致すれば 1、違えば 0。BR4.3）
     */
    public int deleteByPreviewId(UUID previewId) {
        return entityManager
                .createQuery("delete from DslPreviewRecord p where p.previewId = :previewId")
                .setParameter("previewId", previewId)
                .executeUpdate();
    }
}
