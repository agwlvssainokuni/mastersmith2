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

import java.util.Objects;

/** 適用中のモデルの提供口の結果（契約 C8 の {@code ActiveDsl}）。適用中の DSL がある（{@link Present}）か、無い（{@link Absent}）か。 */
public sealed interface ActiveDsl {

    /**
     * 適用中の DSL がある。
     *
     * @param model 適用中のモデル（変更できない値の木）
     * @param dslHash 適用中の DSL の識別
     */
    record Present(DslModel model, String dslHash) implements ActiveDsl {

        /** モデルと識別が必須で、互いに合っていることを確かめる。 */
        public Present {
            Objects.requireNonNull(model, "model は必須です");
            Objects.requireNonNull(dslHash, "dslHash は必須です");
            if (!model.dslHash().equals(dslHash)) {
                throw new IllegalArgumentException("dslHash がモデルの識別と合いません");
            }
        }
    }

    /** 適用中の DSL が無い（初めての導入、または起動の途中）。 */
    record Absent() implements ActiveDsl {}

    /**
     * モデルから結果を作る。
     *
     * @param model モデル（null は「無い」）
     * @return ある・無い のどちらか
     */
    static ActiveDsl of(DslModel model) {
        return model == null ? new Absent() : new Present(model, model.dslHash());
    }
}
