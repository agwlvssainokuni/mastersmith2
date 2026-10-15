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

import cherry.mastersmith.navigation.domain.NavTableRef;

/**
 * 項目が指すテーブルの組の応答（{@code entities.md} の ENT-003）。名前はエンコードせずに返す。
 *
 * @param schemaName スキーマの名前
 * @param tableName テーブルの名前
 */
public record NavTableRefResponse(String schemaName, String tableName) {

    /**
     * テーブルの組から作る。
     *
     * @param ref テーブルの組（null なら null を返す）
     * @return 応答（無ければ null）
     */
    public static NavTableRefResponse from(NavTableRef ref) {
        return ref == null ? null : new NavTableRefResponse(ref.schemaName(), ref.tableName());
    }

    /**
     * 値を伏せた文字列を返す（Intent 261015-deps-ui-carryover の FR4.1）。{@code TraceAspect} が引数と戻り値を TRACE のログに文字列で出す
     * ため、DSL のスキーマ・テーブルの名前を出さない。
     *
     * @return 値を伏せた文字列
     */
    @Override
    public String toString() {
        return "NavTableRefResponse[schemaName=***, tableName=***]";
    }
}
