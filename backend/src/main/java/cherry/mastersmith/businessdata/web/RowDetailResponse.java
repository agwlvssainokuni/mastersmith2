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

import cherry.mastersmith.businessdata.service.RowReadResult;
import java.util.List;
import java.util.Map;

/**
 * 詳細の API の応答（契約 C2 の {@code RowDetail} に、機能設計の X3 の {@code truncatedColumns} を足した形）。文字列にするときは値を伏せる
 * （BR10.1）。
 *
 * @param definitionId 今の定義の識別
 * @param definitionChanged 要求の識別が今と違うか
 * @param key 行を指す主キー
 * @param values 詳細の列の値（password と BINARY のカラムは含めない）
 * @param truncatedColumns 上限の文字数で切ったカラム
 */
public record RowDetailResponse(
        DefinitionIdBody definitionId,
        boolean definitionChanged,
        Map<String, String> key,
        Map<String, String> values,
        List<String> truncatedColumns) {

    /**
     * 詳細の結果から作る。
     *
     * @param found 結果
     * @return 応答
     */
    static RowDetailResponse from(RowReadResult.Found found) {
        return new RowDetailResponse(
                DefinitionIdBody.from(found.view().definitionId()),
                found.definitionChanged(),
                found.row().key() == null ? Map.of() : found.row().key().values(),
                found.row().values().values(),
                List.copyOf(found.row().values().truncatedColumns()));
    }

    /** カラムの数だけを出す（名前と値を出さない）。 */
    @Override
    public String toString() {
        return "RowDetailResponse[key=" + key.size() + ", values=" + values.size() + ", truncated="
                + truncatedColumns.size() + "]";
    }
}
