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

import cherry.mastersmith.businessdata.domain.KeyInput;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.domain.WriteOperation;
import cherry.mastersmith.businessdata.domain.WriteRequest;
import cherry.mastersmith.businessdata.domain.WriteValues;
import java.util.Map;

/**
 * 更新の API の本文（{@code PATCH /api/tables/rows}、Intent 261016-business-data-views の U2）。値は変えたカラムだけ。文字列にするときは
 * 主キーと値を伏せる（BR12.1）。
 *
 * @param schema スキーマ名
 * @param table テーブル名
 * @param definitionId 画面が持っている定義の識別（書き込みでは必須）
 * @param key 主キーのカラムと値
 * @param values 変えたカラムと値
 */
public record RowUpdateRequest(
        String schema,
        String table,
        DefinitionIdBody definitionId,
        Map<String, String> key,
        Map<String, String> values) {

    /**
     * 確かめる前の要求にする。
     *
     * @return 要求
     */
    WriteRequest toRequest() {
        return new WriteRequest(
                new TableQuery(schema, table, DefinitionIdBody.toRequested(definitionId)),
                WriteOperation.UPDATE,
                new KeyInput(key),
                new WriteValues(values));
    }

    /** テーブルの名前と、主キー・値のカラムの数だけを出す。 */
    @Override
    public String toString() {
        return "RowUpdateRequest[schema=" + schema + ", table=" + table + ", keyColumns="
                + (key == null ? 0 : key.size()) + ", valueColumns=" + (values == null ? 0 : values.size()) + "]";
    }
}
