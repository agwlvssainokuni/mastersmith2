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
package cherry.mastersmith.navigation.domain;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.Objects;

/**
 * テーブルの置き場の問い合わせの業務処理の結果（{@code entities.md} の ENT-008、BR5.2・BR5.3・BR7.2）。画面入出力の層が {@code switch}
 * で場合を尽くして応答に変える。
 */
public sealed interface TableAccessResult {

    /**
     * 見られる（実効の主権限が READ・FULL）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前
     * @param displayName テーブルの表示名
     * @param main 実効の主権限（READ か FULL）
     */
    record Visible(String schemaName, String tableName, DisplayName displayName, MainPermission main)
            implements TableAccessResult {

        /** 必須の値と、主権限が READ・FULL であることを確かめる。 */
        public Visible {
            Objects.requireNonNull(schemaName, "schemaName は必須です");
            Objects.requireNonNull(tableName, "tableName は必須です");
            Objects.requireNonNull(displayName, "displayName は必須です");
            Objects.requireNonNull(main, "main は必須です");
            if (main == MainPermission.NONE) {
                throw new IllegalArgumentException("見られる結果の主権限は READ か FULL です");
            }
        }
    }

    /** 見られない（NONE・DSL に無い・DSL が無いを区別しない。理由を持たない。BR5.2）。 */
    record NotVisible() implements TableAccessResult {}
}
