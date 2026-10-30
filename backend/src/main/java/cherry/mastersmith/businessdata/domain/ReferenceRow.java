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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 参照先の行1つ（Intent 261029-reference-lookup-cleanup の U1、契約 C2、entities.md の ReferenceRow）。
 *
 * <p>文字列にするときは値・ラベル・セルを伏せ、セルの数だけを出す（BR10.1）。
 *
 * @param value 値のカラムの値（切らない。BR7.2・BR7.3）
 * @param label ラベル（一覧の上限で切ったもの。NULL・バイナリ・password は null。BR7.3〜BR7.5）
 * @param cells 一覧の列の値（検索のときピッカーの一覧の列の並び。null を含む。ラベルの1件読みでは空。BR5.9・BR6.3）
 */
public record ReferenceRow(String value, String label, List<String> cells) {

    /** 値が必須で、セルを null の要素を許したまま変更できないものにする。 */
    public ReferenceRow {
        Objects.requireNonNull(value, "value は必須です");
        cells = cells == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(cells));
    }

    /** 値・ラベル・セルを伏せる（BR10.1）。 */
    @Override
    public String toString() {
        return "ReferenceRow[value=***, label=" + (label == null ? "null" : "***") + ", cells=" + cells.size() + "]";
    }
}
