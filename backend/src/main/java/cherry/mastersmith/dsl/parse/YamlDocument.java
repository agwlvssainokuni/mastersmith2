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

import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * 読み込んだ YAML の、検証用の JSON の形と位置の対応表。
 *
 * @param json 検証用の JSON の形（空の文書は null の節）
 * @param positions 位置の対応表
 */
public record YamlDocument(JsonNode json, PositionMap positions) {

    /** 両方が必須であることを確かめる。 */
    public YamlDocument {
        Objects.requireNonNull(json, "json は必須です");
        Objects.requireNonNull(positions, "positions は必須です");
    }
}
