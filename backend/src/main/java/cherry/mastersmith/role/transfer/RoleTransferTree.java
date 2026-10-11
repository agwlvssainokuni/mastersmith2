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
package cherry.mastersmith.role.transfer;

import cherry.mastersmith.dsl.service.YamlPositions;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * 上限つきの読み込み（{@code SafeYamlReader}）で読めた権限の YAML の木と、位置を引く口（NFR1.9）。中身の検証の前の形。
 *
 * <p>{@link #toString()} は位置の記録の数だけを出し、木の中身を出さない。
 *
 * @param tree JSON の木（空の文書は null の節）
 * @param positions 位置を引く口
 */
public record RoleTransferTree(JsonNode tree, YamlPositions positions) {

    /** 両方が必須であることを確かめる。 */
    public RoleTransferTree {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(positions, "positions");
    }

    /** 位置の記録の数だけを出す。 */
    @Override
    public String toString() {
        return "RoleTransferTree[positions=" + positions.size() + "]";
    }
}
