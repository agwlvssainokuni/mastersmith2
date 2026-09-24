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

import java.util.Objects;

/**
 * 今の状態（契約 C6 の {@code DslStatus}、BR6.3）。
 *
 * @param applied 適用中の版（無ければ null）
 * @param preview 今のプレビュー（無ければ null）
 */
public record DslStatus(Applied applied, Preview preview) {

    /**
     * 適用中の版と、適用した管理者。
     *
     * @param ref 版の参照
     * @param by 適用した管理者
     */
    public record Applied(DslAppliedRef ref, DslUserRef by) {

        /** 必須の値を確かめる。 */
        public Applied {
            Objects.requireNonNull(ref, "ref は必須です");
            Objects.requireNonNull(by, "by は必須です");
        }
    }

    /**
     * 今のプレビューと、置いた管理者。
     *
     * @param ref プレビューの参照
     * @param by 置いた管理者
     */
    public record Preview(DslPreviewRef ref, DslUserRef by) {

        /** 必須の値を確かめる。 */
        public Preview {
            Objects.requireNonNull(ref, "ref は必須です");
            Objects.requireNonNull(by, "by は必須です");
        }
    }

    /**
     * 履歴の1件（BR6.1）。
     *
     * @param applied 版と適用した管理者
     * @param current 今適用中の版なら true
     */
    public record HistoryEntry(Applied applied, boolean current) {

        /** 必須の値を確かめる。 */
        public HistoryEntry {
            Objects.requireNonNull(applied, "applied は必須です");
        }
    }
}
