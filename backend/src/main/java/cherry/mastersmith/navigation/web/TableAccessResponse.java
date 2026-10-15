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
package cherry.mastersmith.navigation.web;

import cherry.mastersmith.navigation.domain.TableAccessResult;

/**
 * テーブルの置き場の問い合わせの 200 の応答（{@code entities.md} の ENT-006、BR5.3）。
 *
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前
 * @param displayName テーブルの表示名の組
 * @param main 実効の主権限（{@code READ}・{@code FULL}）
 */
public record TableAccessResponse(String schemaName, String tableName, DisplayLabelResponse displayName, String main) {

    /**
     * 見られる結果から作る。
     *
     * @param visible 見られる結果
     * @return 応答
     */
    public static TableAccessResponse from(TableAccessResult.Visible visible) {
        return new TableAccessResponse(
                visible.schemaName(),
                visible.tableName(),
                DisplayLabelResponse.from(visible.displayName()),
                visible.main().name());
    }

    /**
     * 実効の主権限だけを出す（Intent 261015-deps-ui-carryover の FR4.1）。{@code TraceAspect} が画面入出力の層の戻り値を TRACE のログに
     * 文字列で出すため、スキーマ・テーブルの名前と DSL の表示名を出さない。応答の本文は項目の値をそのまま載せる（この文字列は使わない）。
     *
     * @return 値を伏せた文字列
     */
    @Override
    public String toString() {
        return "TableAccessResponse[schemaName=***, tableName=***, displayName=***, main=" + main + "]";
    }
}
