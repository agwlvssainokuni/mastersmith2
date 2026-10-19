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
package cherry.mastersmith.businessdata.domain;

import java.util.Objects;

/**
 * 作成・更新・削除の要求1件（確かめる前。Intent 261016-business-data-views の U2、entities.md の WriteRequest、BR12.1）。スキーマ名・
 * テーブル名・主キー・値はすべて本文で運び、操作は道から決める。
 *
 * <p>文字列にするときは主キーと値を伏せる（カラムの数だけを出す）。
 *
 * @param query 要求が指すテーブルと定義の識別
 * @param operation 操作
 * @param key 主キー（更新・削除。作成では空）
 * @param values 値（作成・更新。削除では空）
 */
public record WriteRequest(TableQuery query, WriteOperation operation, KeyInput key, WriteValues values) {

    /** 必須の値を確かめ、無い主キー・値を空にする。 */
    public WriteRequest {
        Objects.requireNonNull(query, "query は必須です");
        Objects.requireNonNull(operation, "operation は必須です");
        key = key == null ? new KeyInput(null) : key;
        values = values == null ? new WriteValues(null) : values;
    }

    /** 操作と、主キー・値のカラムの数だけを出す。 */
    @Override
    public String toString() {
        return "WriteRequest[operation=" + operation + ", keyColumns="
                + key.values().size() + ", valueColumns=" + values.values().size() + "]";
    }
}
