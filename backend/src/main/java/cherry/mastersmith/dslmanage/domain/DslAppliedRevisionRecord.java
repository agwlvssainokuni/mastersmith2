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
 * 適用の履歴の1件（表 {@code dsl_applied_revisions}。{@code entities.md} の DslAppliedRevision）。
 *
 * <p>行を足すのはプレビューの行からの1文の写し（{@code DslAppliedRevisionRepository#copyFromPreview}）、消すのは件数の上限を超えた
 * 古い行の削除で、このエンティティは読み取りだけに使う。主キーは追加の順（DB の連番）で、版の識別（{@code revisionId}）は一意の別の
 * 列に持つ。API の応答として直接返さない。文字列化では本文を出さない。
 */
@Entity
@Table(name = "dsl_applied_revisions")
public class DslAppliedRevisionRecord {

    @Id
    @Column(name = "sequence_no")
    private Long sequenceNo;

    @Column(name = "revision_id", nullable = false, updatable = false)
    private UUID revisionId;

    @Lob
    @Column(name = "yaml_bytes", nullable = false, updatable = false)
    private byte[] yamlBytes;

    @Column(name = "dsl_hash", nullable = false, updatable = false, length = 64)
    private String dslHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 16)
    private DslSource source;

    @Column(name = "applied_by_user_id", nullable = false, updatable = false)
    private long appliedByUserId;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    /** JPA が使う。 */
    protected DslAppliedRevisionRecord() {}

    /**
     * 本文を持たない参照を返す。
     *
     * @return 参照
     */
    public DslAppliedRef toRef() {
        return new DslAppliedRef(revisionId, dslHash, source, appliedByUserId, appliedAt);
    }

    /**
     * 本文と参照の組を返す（本文は写す）。
     *
     * @return 本文と参照
     */
    public DslContent<DslAppliedRef> toContent() {
        return new DslContent<>(toRef(), yamlBytes);
    }

    /** 本文を出さない文字列。 */
    @Override
    public String toString() {
        return "DslAppliedRevisionRecord[sequenceNo=" + sequenceNo + ", revisionId=" + revisionId + ", dslHash="
                + dslHash + ", source=" + source + ", appliedByUserId=" + appliedByUserId + ", appliedAt=" + appliedAt
                + ", bytes=" + (yamlBytes == null ? 0 : yamlBytes.length) + "]";
    }
}
