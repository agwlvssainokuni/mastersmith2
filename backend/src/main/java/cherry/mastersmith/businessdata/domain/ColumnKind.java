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
 * カラム1つの受け渡しの型と、等しさで行を指せるか（BR7.1・BR7.9）。DB の種類と DSL の型から {@code repository} の層が決め、出し分けの
 * 関数（{@link TableAccessPolicy}）に渡す。
 *
 * @param valueType 受け渡しの型
 * @param equalsSupported 完全一致の検索と主キーの指し方に使えるなら true（BR7.9 の型は false）
 * @param fractionDigits 日時・時刻の型の秒の端数の桁の上限（DB の型の精度。ほかの型は null。Intent 261016-business-data-views の U2、
 *     NFR 設計 Q6: A）
 */
public record ColumnKind(ValueType valueType, boolean equalsSupported, Integer fractionDigits) {

    /** 型が必須であることを確かめる。 */
    public ColumnKind {
        Objects.requireNonNull(valueType, "valueType は必須です");
    }

    /**
     * 秒の端数の桁の上限を持たない型を作る。
     *
     * @param valueType 受け渡しの型
     * @param equalsSupported 完全一致の検索と主キーの指し方に使えるなら true
     */
    public ColumnKind(ValueType valueType, boolean equalsSupported) {
        this(valueType, equalsSupported, null);
    }
}
