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
package cherry.mastersmith.dsl.parse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 位置の対応表（ADR-008）。場所（JSON Pointer の形）から、YAML の行・列を引く。
 *
 * <p>対応表の項目は、その項目のキーの位置を持つ（値の位置ではない）。並びの要素はその要素が始まる位置、文書の根は文書が始まる
 * 位置を持つ。別名で参照した値の中は、参照を書いた場所の位置を持つ（BR4.2）。
 */
public final class PositionMap {

    private final Map<String, YamlPosition> positions = new HashMap<>();

    /**
     * 場所の位置を記録する（すでにあれば変えない）。
     *
     * @param pointer 場所
     * @param position 位置（null なら記録しない）
     */
    void put(String pointer, YamlPosition position) {
        if (position != null) {
            positions.putIfAbsent(pointer, position);
        }
    }

    /**
     * 場所の位置を引く。
     *
     * @param pointer 場所（JSON Pointer の形）
     * @return 位置（記録が無ければ空）
     */
    public Optional<YamlPosition> find(String pointer) {
        return Optional.ofNullable(positions.get(pointer));
    }

    /**
     * 記録した場所の数を返す。
     *
     * @return 場所の数
     */
    public int size() {
        return positions.size();
    }
}
