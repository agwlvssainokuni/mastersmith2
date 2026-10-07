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

/**
 * 起動時の読み方（メニューの深さだけを検証から外し、深すぎる枝を落とす。U2 dsl-v2 の BR2.3、functional-spec.md の 2節）の結果。
 * 検証を通った（{@link Valid}）か、深さ以外の誤りで通らなかった（{@link Invalid}）か。
 */
public sealed interface DslStartupReadResult {

    /**
     * 深さ以外の検証を通った。
     *
     * @param model 深すぎる枝を落とした後のモデル
     * @param prunedMenuItems 落としたメニューの項目の数（0 以上）
     */
    record Valid(DslModel model, int prunedMenuItems) implements DslStartupReadResult {

        /** モデルが必須で、数が 0 以上であることを確かめる。 */
        public Valid {
            Objects.requireNonNull(model, "model は必須です");
            if (prunedMenuItems < 0) {
                throw new IllegalArgumentException("prunedMenuItems は 0 以上です");
            }
        }
    }

    /**
     * 深さ以外の誤りで通らなかった（書式の版 1 を含む）。
     *
     * @param errors 誤りの一覧（1件以上）
     */
    record Invalid(List<DslError> errors) implements DslStartupReadResult {

        /** 誤りが1件以上あることを確かめ、変更できない一覧にする。 */
        public Invalid {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors は必須です"));
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors は1件以上です");
            }
        }
    }
}
