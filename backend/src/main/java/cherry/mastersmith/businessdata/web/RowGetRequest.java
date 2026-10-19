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
import java.util.Map;

/**
 * 詳細の API の本文（{@code POST /api/tables/rows/get}、契約 C2 の {@code RowKeyRequest}）。主キーの値はカラムごとに分けて受ける。文字列に
 * するときはカラムの数だけを出す（BR10.1）。
 *
 * @param schema スキーマ名
 * @param table テーブル名
 * @param definitionId 画面が持っている定義の識別（任意）
 * @param key 主キーのカラムと値
 */
public record RowGetRequest(String schema, String table, DefinitionIdBody definitionId, Map<String, String> key) {

    /**
     * 確かめる前の主キーにする。
     *
     * @return 主キー
     */
    KeyInput toInput() {
        return new KeyInput(key);
    }

    /** テーブルの名前と主キーのカラムの数だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "RowGetRequest[schema=" + schema + ", table=" + table + ", keyColumns=" + (key == null ? 0 : key.size())
                + "]";
    }
}
