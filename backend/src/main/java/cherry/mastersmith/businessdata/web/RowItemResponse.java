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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.RowRecord;
import java.util.List;
import java.util.Map;

/**
 * 一覧の1件（契約 C2 の {@code RowItem} に、機能設計の X3 の {@code truncatedColumns} を足した形）。値は文字か null。文字列にするときは
 * 値を伏せる（BR10.1）。
 *
 * @param key 行を指す主キー（DETAIL が許されないテーブルでは null）
 * @param values 一覧の列の値（password と BINARY のカラムは含めない）
 * @param truncatedColumns 上限の文字数で切ったカラム
 */
public record RowItemResponse(Map<String, String> key, Map<String, String> values, List<String> truncatedColumns) {

    /**
     * 読んだ1行から作る。
     *
     * @param row 行
     * @return 応答の1件
     */
    static RowItemResponse from(RowRecord row) {
        return new RowItemResponse(
                row.key() == null ? null : row.key().values(),
                row.values().values(),
                List.copyOf(row.values().truncatedColumns()));
    }

    /** カラムの数だけを出す（名前と値を出さない）。 */
    @Override
    public String toString() {
        return "RowItemResponse[key=" + (key == null ? "none" : key.size()) + ", values=" + values.size()
                + ", truncated=" + truncatedColumns.size() + "]";
    }
}
