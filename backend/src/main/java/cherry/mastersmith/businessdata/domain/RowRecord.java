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
 * 読んだ1行（一覧の1件・詳細）。行を指す主キーの値（切らない）と、応答に出すカラムの値を持つ（BR2.2）。
 *
 * <p>文字列にするときは値を伏せる（BR10.1）。
 *
 * @param key 主キーのカラムと値（DETAIL が許されないテーブルでは null）
 * @param values 応答に出すカラムの値
 */
public record RowRecord(RowValues key, RowValues values) {

    /** 値が必須であることを確かめる。 */
    public RowRecord {
        Objects.requireNonNull(values, "values は必須です");
    }

    /** 主キーの有無と、カラムの数だけを出す。 */
    @Override
    public String toString() {
        return "RowRecord[key=" + (key == null ? "none" : key) + ", values=" + values + "]";
    }
}
