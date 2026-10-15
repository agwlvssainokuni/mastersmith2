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
 * @param appliedUnreadable 適用中の版を起動時に今の書式で読めず、適用中の DSL が使われていないなら true（U2 dsl-v2 の BR3.6、
 *     entities.md の DslStatusV2。判定は NFR 設計の 4.8 のとおり起動時に読めなかった版の ID との比べ）
 * @param prunedMenuItems 適用中の版を起動時に読んだときに落とした、深さの上限を超えるメニューの項目の数（落としていなければ 0。
 *     Intent 261015-deps-ui-carryover の FR3.2。判定は {@code appliedUnreadable} と同じく起動時に覚えた版の ID との比べ）
 */
public record DslStatus(Applied applied, Preview preview, boolean appliedUnreadable, int prunedMenuItems) {

    /** 落とした項目の数が負でないことを確かめる。 */
    public DslStatus {
        if (prunedMenuItems < 0) {
            throw new IllegalArgumentException("prunedMenuItems は 0 以上です");
        }
    }

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
