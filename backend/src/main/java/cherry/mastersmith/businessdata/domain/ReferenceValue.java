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
 * 参照先の値1つ（Intent 261029-reference-lookup-cleanup の U1、契約 C1、entities.md の ReferenceValue）。ラベルの1件読みと保存前の確かめ
 * （{@code check}）に使う。受け取ったままの文字で持ち、値のカラムの DSL の型に変えてから束ねる（BR7.1）。
 *
 * <p>文字列にするときは値を伏せる（BR10.1）。
 *
 * @param value 値（受け取ったままの文字）
 */
public record ReferenceValue(String value) {

    /** 値が必須であることを確かめる。 */
    public ReferenceValue {
        Objects.requireNonNull(value, "value は必須です");
    }

    /** 値を伏せる（BR10.1）。 */
    @Override
    public String toString() {
        return "ReferenceValue[value=***]";
    }
}
