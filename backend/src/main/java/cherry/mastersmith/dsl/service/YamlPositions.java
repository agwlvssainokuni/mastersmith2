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
package cherry.mastersmith.dsl.service;

import cherry.mastersmith.dsl.parse.PositionMap;
import java.util.Objects;
import java.util.Optional;

/**
 * 読めた YAML の位置を引く口（U2 の機能設計の BR6.4）。場所（JSON Pointer の形。例 {@code /roles/0/name}、文書の根は
 * {@code ""}）から、YAML の行・列（1 から）を引く。
 *
 * <p>{@code dsl.parse} の位置の対応表を包み、その型を {@code dsl} の外へ出さない（境界テスト {@code parseAndValidateStayInside}）。
 * 対応表の項目はそのキーの位置、並びの要素はその要素が始まる位置、別名で参照した値の中は参照を書いた場所の位置を持つ。
 */
public final class YamlPositions {

    private final PositionMap positions;

    /**
     * 作る（このパッケージの中だけで作る）。
     *
     * @param positions 位置の対応表
     */
    YamlPositions(PositionMap positions) {
        this.positions = Objects.requireNonNull(positions, "positions は必須です");
    }

    /**
     * 場所の位置を引く。
     *
     * @param pointer 場所（JSON Pointer の形）
     * @return 位置（記録が無ければ空）
     */
    public Optional<Location> find(String pointer) {
        return positions.find(pointer).map(position -> new Location(position.line(), position.column()));
    }

    /**
     * 記録した場所の数を返す。
     *
     * @return 場所の数
     */
    public int size() {
        return positions.size();
    }

    /** 場所の数だけを出す（場所の名前は利用者の書いたキーを含みうるため出さない）。 */
    @Override
    public String toString() {
        return "YamlPositions[size=" + positions.size() + "]";
    }

    /**
     * YAML の中の位置。
     *
     * @param line 行（1 から）
     * @param column 列（1 から）
     */
    public record Location(int line, int column) {

        /** 行・列が 1 以上であることを確かめる。 */
        public Location {
            if (line < 1 || column < 1) {
                throw new IllegalArgumentException("line と column は 1 以上です");
            }
        }
    }
}
