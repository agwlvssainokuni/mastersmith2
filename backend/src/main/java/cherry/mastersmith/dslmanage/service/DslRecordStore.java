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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslPreviewRef;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslAppliedRevisionRepository;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * プレビューと適用の履歴の保存（トランザクションの境界）。{@link DslLifecycle} が使う。
 *
 * <p>トランザクションは内部DB の短い読み書きだけを包む。対象DB の読み取り（生成・照合）と U2 の読み込みは、ここの外で行う（内部DB の
 * 接続を長く持たないため）。適用は1つのトランザクションで、履歴への追加・previewId を指定したプレビューの削除・上限を超えた古い履歴の
 * 削除を行い、どこかで失敗したら全部を巻き戻す（BR4.2・BR4.3、NFR8.1・NFR8.2）。
 */
@Service
public class DslRecordStore {

    private final DslPreviewRepository previewRepository;

    private final DslAppliedRevisionRepository revisionRepository;

    /**
     * 作る。
     *
     * @param previewRepository プレビューの表
     * @param revisionRepository 適用の履歴の表
     */
    public DslRecordStore(DslPreviewRepository previewRepository, DslAppliedRevisionRepository revisionRepository) {
        this.previewRepository = previewRepository;
        this.revisionRepository = revisionRepository;
    }

    /**
     * プレビューを置く（今のプレビューを置き換え、新しい previewId を振る。BR1.4）。
     *
     * @param yamlBytes 本文
     * @param dslHash DSL の識別
     * @param source 出どころ
     * @param actorUserId 置いた管理者
     * @param placedAt 置いた日時
     * @return 置いたプレビューの参照
     */
    @Transactional
    public DslPreviewRef placePreview(
            byte[] yamlBytes, String dslHash, DslSource source, long actorUserId, Instant placedAt) {
        UUID previewId = UUID.randomUUID();
        previewRepository.place(previewId, yamlBytes, dslHash, source, actorUserId, placedAt);
        return new DslPreviewRef(previewId, dslHash, source, actorUserId, placedAt);
    }

    /**
     * 今のプレビューの参照を返す（本文を読まない）。
     *
     * @return 参照（無ければ空）
     */
    @Transactional(readOnly = true)
    public Optional<DslPreviewRef> findPreviewRef() {
        return previewRepository.findRef();
    }

    /**
     * 今のプレビューの本文と参照を返す。
     *
     * @return 本文と参照（無ければ空）
     */
    @Transactional(readOnly = true)
    public Optional<DslContent<DslPreviewRef>> findPreviewContent() {
        return previewRepository.findContent();
    }

    /**
     * previewId を指定してプレビューを消す（BR3.1）。
     *
     * @param previewId プレビューの識別
     * @return 消した行の数（ほかの操作で置き換わっていれば 0）
     */
    @Transactional
    public int discardPreview(UUID previewId) {
        return previewRepository.deleteByPreviewId(previewId);
    }

    /**
     * 適用する（1つのトランザクション。BR4.1〜BR4.5）。
     *
     * @param previewId 要求で指定されたプレビューの識別
     * @param actorUserId 適用した管理者
     * @param appliedAt 適用した日時
     * @param historyLimit 履歴の件数の上限
     * @return 足した版の参照
     * @throws BusinessException プレビューが無い・previewId が違う・同時の適用に負けた（409 {@code DSL_PREVIEW_CHANGED}。巻き戻す）
     */
    @Transactional
    public DslAppliedRef apply(UUID previewId, long actorUserId, Instant appliedAt, int historyLimit) {
        DslPreviewRef preview = previewRepository
                .findRef()
                .filter(ref -> ref.previewId().equals(previewId))
                .orElseThrow(DslRecordStore::previewChanged);
        UUID revisionId = UUID.randomUUID();
        if (revisionRepository.copyFromPreview(revisionId, previewId, actorUserId, appliedAt) != 1) {
            throw previewChanged();
        }
        if (previewRepository.deleteByPreviewId(previewId) != 1) {
            throw previewChanged();
        }
        revisionRepository.deleteOlderThanNewest(historyLimit);
        return new DslAppliedRef(revisionId, preview.dslHash(), preview.source(), actorUserId, appliedAt);
    }

    /**
     * 適用中の版の参照を返す（本文を読まない）。
     *
     * @return 参照（無ければ空）
     */
    @Transactional(readOnly = true)
    public Optional<DslAppliedRef> findCurrentRevision() {
        return revisionRepository.findCurrentRef();
    }

    /**
     * 履歴の参照を新しい順に返す（本文を読まない）。
     *
     * @return 参照の一覧
     */
    @Transactional(readOnly = true)
    public List<DslAppliedRef> findRevisionsNewestFirst() {
        return revisionRepository.findRefsNewestFirst();
    }

    /**
     * 適用中の版の本文と参照を返す（起動時の読み込み）。
     *
     * @return 本文と参照（無ければ空）
     */
    @Transactional(readOnly = true)
    public Optional<DslContent<DslAppliedRef>> findCurrentRevisionContent() {
        return revisionRepository.findCurrentContent();
    }

    /**
     * 版の本文と参照を返す。
     *
     * @param revisionId 版の識別
     * @return 本文と参照（無ければ空）
     */
    @Transactional(readOnly = true)
    public Optional<DslContent<DslAppliedRef>> findRevisionContent(UUID revisionId) {
        return revisionRepository.findContent(revisionId);
    }

    private static BusinessException previewChanged() {
        return new BusinessException(DslProblemTypes.DSL_PREVIEW_CHANGED);
    }
}
