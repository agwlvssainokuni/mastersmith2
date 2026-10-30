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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 更新で、更新前の行から読んだ参照のカラムの値（Intent 261029-reference-lookup-cleanup の U2、entities.md の
 * PreviousReferenceValues、BR2.4〜BR2.6）。比べるためだけに持ち、比べ終えたら捨てる。読めない・上限で切られた値は持たない
 * （「読めない」として扱い、本文の値と「違う」とする。BR2.5）。
 *
 * <p>{@code Map} のまま層をまたがないための型。文字列にするときはカラムの名前の一覧だけを出し、値を出さない（BR7.1）。
 *
 * @param readable 読めたカラムの値（NULL は {@link BoundValue#value()} が null。並びは DSL の並び。変更できない）
 */
public record PreviousReferenceValues(Map<String, BoundValue> readable) {

    /** 対応表を、順を保ったまま変更できないものにする。 */
    public PreviousReferenceValues {
        Objects.requireNonNull(readable, "readable は必須です");
        readable = Collections.unmodifiableMap(new LinkedHashMap<>(readable));
    }

    /**
     * 更新前の値を返す。
     *
     * @param column 参照元のカラムの名前
     * @return 型つきの値（読めない・切られたときは空）
     */
    public Optional<BoundValue> previous(String column) {
        return Optional.ofNullable(readable.get(column));
    }

    /** カラムの名前の一覧だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "PreviousReferenceValues[columns=" + readable.keySet() + "]";
    }
}
