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
package cherry.mastersmith.dsl.domain;

import java.util.List;
import java.util.Objects;

/** DSL の読み込みの結果（契約 C4 の {@code DslReadResult}）。検証を通った（{@link Valid}）か、通らなかった（{@link Invalid}）か。 */
public sealed interface DslReadResult {

    /**
     * 検証を通った。
     *
     * @param model モデル
     * @param dslHash 本文のバイト列の識別（モデルの識別と同じ）
     */
    record Valid(DslModel model, String dslHash) implements DslReadResult {

        /** モデルと識別が必須で、互いに合っていることを確かめる。 */
        public Valid {
            Objects.requireNonNull(model, "model は必須です");
            Objects.requireNonNull(dslHash, "dslHash は必須です");
            if (!model.dslHash().equals(dslHash)) {
                throw new IllegalArgumentException("dslHash がモデルの識別と合いません");
            }
        }
    }

    /**
     * 検証を通らなかった。
     *
     * @param errors 誤りの一覧（1件以上。件数の上限を設けない。絞るのは U4）
     */
    record Invalid(List<DslError> errors) implements DslReadResult {

        /** 誤りが1件以上あることを確かめ、変更できない一覧にする。 */
        public Invalid {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors は必須です"));
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors は1件以上です");
            }
        }
    }

    /**
     * 検証を通った結果を作る。
     *
     * @param model モデル
     * @return 検証を通った結果
     */
    static DslReadResult valid(DslModel model) {
        return new Valid(model, model.dslHash());
    }

    /**
     * 検証を通らなかった結果を作る。
     *
     * @param errors 誤りの一覧
     * @return 検証を通らなかった結果
     */
    static DslReadResult invalid(List<DslError> errors) {
        return new Invalid(errors);
    }
}
