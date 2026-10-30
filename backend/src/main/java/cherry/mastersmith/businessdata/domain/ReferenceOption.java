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
 * REFERENCE の選択肢1つ（Intent 261029-reference-lookup-cleanup の U1、契約 C2、entities.md の ReferenceOption）。
 *
 * <p>文字列にするときは値とラベルを伏せ、ラベルの有無だけを出す（BR10.1）。
 *
 * @param value 値のカラムの値（既存の型の対応で文字にしたもの。切らない。BR7.2・BR7.3）
 * @param label ラベル（一覧の上限で切ったもの。NULL・バイナリ・password は null。BR7.3〜BR7.5）
 */
public record ReferenceOption(String value, String label) {

    /** 値が必須であることを確かめる。 */
    public ReferenceOption {
        Objects.requireNonNull(value, "value は必須です");
    }

    /** 値とラベルを伏せる（BR10.1）。 */
    @Override
    public String toString() {
        return "ReferenceOption[value=***, label=" + (label == null ? "null" : "***") + "]";
    }
}
