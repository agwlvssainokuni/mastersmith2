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
package cherry.mastersmith.dslmanage.domain;

import cherry.mastersmith.dsl.domain.DslErrorKind;
import java.util.Objects;

/**
 * 応答に載せる DSL の誤り1件（契約 C6 の {@code DslInvalidProblem.errors} の要素）。文言は要求の表示言語で、部品の例外の文言を
 * 含めない（BR1.5、NFR5.4）。
 *
 * @param kind 種類
 * @param line YAML の行（無ければ null）
 * @param column YAML の列（無ければ null）
 * @param path DSL の中の場所（無ければ null）
 * @param message 文言
 */
public record DslErrorItem(DslErrorKind kind, Integer line, Integer column, String path, String message) {

    /** 必須の値を確かめる。 */
    public DslErrorItem {
        Objects.requireNonNull(kind, "kind は必須です");
        Objects.requireNonNull(message, "message は必須です");
    }
}
