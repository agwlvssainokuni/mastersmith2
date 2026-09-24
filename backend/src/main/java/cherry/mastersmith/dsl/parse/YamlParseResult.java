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

import cherry.mastersmith.dsl.domain.DslError;
import java.util.Objects;

/** YAML の読み込みの結果。読めた（{@link Parsed}）か、読み込みの段で止めた（{@link Rejected}）か。 */
public sealed interface YamlParseResult {

    /**
     * 読めた。
     *
     * @param document 検証用の JSON の形と位置の対応表
     */
    record Parsed(YamlDocument document) implements YamlParseResult {

        /** 文書が必須であることを確かめる。 */
        public Parsed {
            Objects.requireNonNull(document, "document は必須です");
        }
    }

    /**
     * 読み込みの段で止めた（上限・タグ・重複キー・YAML として読めない）。
     *
     * @param error 誤り（読み込みの段は最初の1件で止める）
     */
    record Rejected(DslError error) implements YamlParseResult {

        /** 誤りが必須であることを確かめる。 */
        public Rejected {
            Objects.requireNonNull(error, "error は必須です");
        }
    }
}
