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
 * 今のプレビューの参照（本文を持たない。今の状態・プレビューの表示・適用の確かめに使う）。
 *
 * @param previewId プレビューの識別（置くたびに新しくなる）
 * @param dslHash DSL の識別（本文のバイト列の SHA-256 の 16 進数）
 * @param source 出どころ
 * @param placedByUserId 置いた管理者の利用者 ID
 * @param placedAt 置いた日時（UTC）
 */
public record DslPreviewRef(UUID previewId, String dslHash, DslSource source, long placedByUserId, Instant placedAt) {

    /** 必須の値を確かめる。 */
    public DslPreviewRef {
        Objects.requireNonNull(previewId, "previewId は必須です");
        Objects.requireNonNull(dslHash, "dslHash は必須です");
        Objects.requireNonNull(source, "source は必須です");
        Objects.requireNonNull(placedAt, "placedAt は必須です");
    }
}
