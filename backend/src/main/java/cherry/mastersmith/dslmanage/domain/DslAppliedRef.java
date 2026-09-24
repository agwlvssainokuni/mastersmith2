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
import java.util.UUID;

/**
 * 適用の履歴の1件の参照（本文を持たない。今の状態と履歴の一覧に使う）。
 *
 * @param revisionId 版の識別
 * @param dslHash DSL の識別
 * @param source 出どころ
 * @param appliedByUserId 適用した管理者の利用者 ID
 * @param appliedAt 適用した日時（UTC）
 */
public record DslAppliedRef(
        UUID revisionId, String dslHash, DslSource source, long appliedByUserId, Instant appliedAt) {

    /** 必須の値を確かめる。 */
    public DslAppliedRef {
        Objects.requireNonNull(revisionId, "revisionId は必須です");
        Objects.requireNonNull(dslHash, "dslHash は必須です");
        Objects.requireNonNull(source, "source は必須です");
        Objects.requireNonNull(appliedAt, "appliedAt は必須です");
    }
}
