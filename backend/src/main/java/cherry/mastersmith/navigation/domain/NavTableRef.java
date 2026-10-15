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

/**
 * メニューの項目が指すテーブルの組（{@code entities.md} の ENT-003、契約 C9）。名前はエンコードせずにそのまま持つ（道への組み立てと
 * エンコードは画面が行う）。
 *
 * @param schemaName スキーマの名前（1文字以上）
 * @param tableName テーブルの名前（1文字以上）
 */
public record NavTableRef(String schemaName, String tableName) {

    /** どちらも空でないことを確かめる。 */
    public NavTableRef {
        if (schemaName == null || schemaName.isEmpty()) {
            throw new IllegalArgumentException("schemaName は必須です");
        }
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("tableName は必須です");
        }
    }

    /**
     * 値を伏せた文字列を返す（Intent 261015-deps-ui-carryover の FR4.1）。{@code TraceAspect} が引数と戻り値を TRACE のログに文字列で出す
     * ため、DSL のスキーマ・テーブルの名前を出さない。
     *
     * @return 値を伏せた文字列
     */
    @Override
    public String toString() {
        return "NavTableRef[schemaName=***, tableName=***]";
    }
}
