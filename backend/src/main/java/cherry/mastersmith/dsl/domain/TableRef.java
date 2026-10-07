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
package cherry.mastersmith.dsl.domain;

/**
 * メニューの項目がテーブルを指す組（書式の版 2、契約 C3 の {@code TableRef}、U2 dsl-v2 の BR1.6）。権限の対象・メニューの置き場の
 * 鍵にも使う。名前は大文字・小文字を区別する。
 *
 * @param schema スキーマ名（1文字以上）
 * @param name テーブル名（1文字以上）
 */
public record TableRef(String schema, String name) {

    /** どちらも空でないことを確かめる。 */
    public TableRef {
        ModelValues.requireName(schema, "table.schema");
        ModelValues.requireName(name, "table.name");
    }
}
