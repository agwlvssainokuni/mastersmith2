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
package cherry.mastersmith.businessdata.service;

import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.ReferenceField;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 定義の API の結果（functional-spec.md の 3.1節）。 */
public sealed interface DefinitionResult permits DefinitionResult.Found, ReadRejected {

    /**
     * 定義を返す。
     *
     * @param view 判定の材料
     * @param definitionChanged 要求の定義の識別が今と違うなら true（BR9.1）
     * @param purpose 用途（Intent 261029-reference-lookup-cleanup の U1、BR4.1・BR12.1）
     * @param references カラムの名前ごとの参照の項目（用途が VIEW なら空。参照でないカラムは含めない）
     */
    record Found(
            TableAccessView view,
            boolean definitionChanged,
            DefinitionPurpose purpose,
            Map<String, ReferenceField> references)
            implements DefinitionResult {

        /** 判定の材料と用途が必須であることを確かめ、対応表を順を保ったまま変更できないものにする。 */
        public Found {
            Objects.requireNonNull(view, "view は必須です");
            Objects.requireNonNull(purpose, "purpose は必須です");
            references = Collections.unmodifiableMap(new LinkedHashMap<>(references));
        }

        /**
         * 用途が VIEW の定義を作る（参照の項目を作らない）。
         *
         * @param view 判定の材料
         * @param definitionChanged 要求の定義の識別が今と違うなら true
         */
        public Found(TableAccessView view, boolean definitionChanged) {
            this(view, definitionChanged, DefinitionPurpose.VIEW, Map.of());
        }

        /** 判定の材料・用途・参照の項目の数だけを出す（値を出さない）。 */
        @Override
        public String toString() {
            return "Found[view=" + view + ", definitionChanged=" + definitionChanged + ", purpose=" + purpose
                    + ", references=" + references.size() + "]";
        }
    }
}
