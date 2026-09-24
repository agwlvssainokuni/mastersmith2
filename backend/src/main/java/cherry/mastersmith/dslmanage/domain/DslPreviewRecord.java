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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 今のプレビュー（表 {@code dsl_previews}、最大1行。{@code entities.md} の DslPreview）。
 *
 * <p>行を置くのは1文の MERGE（{@code DslPreviewRepository#place}）、消すのは previewId を指定した削除で、このエンティティは読み取り
 * だけに使う。本文は受け取ったバイト列のまま持つ（{@code entities.md} の {@code yamlText}（text）との差。NFR 設計 Q3: A）。
 * API の応答として直接返さない。文字列化では本文を出さない。
 */
@Entity
@Table(name = "dsl_previews")
public class DslPreviewRecord {

    /** 行の鍵の固定の値。 */
    public static final int SLOT = 1;

    @Id
    @Column(name = "preview_slot")
    private Integer previewSlot;

    @Column(name = "preview_id", nullable = false, updatable = false)
    private UUID previewId;

    @Lob
    @Column(name = "yaml_bytes", nullable = false, updatable = false)
    private byte[] yamlBytes;

    @Column(name = "dsl_hash", nullable = false, updatable = false, length = 64)
    private String dslHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 16)
    private DslSource source;

    @Column(name = "placed_by_user_id", nullable = false, updatable = false)
    private long placedByUserId;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    /** JPA が使う。 */
    protected DslPreviewRecord() {}

    /**
     * 本文を持たない参照を返す。
     *
     * @return 参照
     */
    public DslPreviewRef toRef() {
        return new DslPreviewRef(previewId, dslHash, source, placedByUserId, placedAt);
    }

    /**
     * 本文と参照の組を返す（本文は写す）。
     *
     * @return 本文と参照
     */
    public DslContent<DslPreviewRef> toContent() {
        return new DslContent<>(toRef(), yamlBytes);
    }

    /** 本文を出さない文字列。 */
    @Override
    public String toString() {
        return "DslPreviewRecord[previewId=" + previewId + ", dslHash=" + dslHash + ", source=" + source
                + ", placedByUserId=" + placedByUserId + ", placedAt=" + placedAt + ", bytes="
                + (yamlBytes == null ? 0 : yamlBytes.length) + "]";
    }
}
