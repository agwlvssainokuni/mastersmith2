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

import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.businessdata.domain.DefinitionId;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.WriteRejection;
import java.util.List;
import java.util.Objects;

/**
 * 作成・更新・削除の API の結果（Intent 261016-business-data-views の U2、entities.md の WriteOutcome、team.md の結果の型）。画面入出力の
 * 層が {@code switch} で場合を尽くして応答か {@code BusinessException} に変える。値を持つ型は文字列にするときに値を伏せる。
 */
public sealed interface WriteResult {

    /**
     * 1行を作った。
     *
     * @param definitionId 今の定義の識別
     * @param key 作った行の主キー（DETAIL が許されるときだけ。無ければ空）
     */
    record Created(DefinitionId definitionId, RowValues key) implements WriteResult {

        /** 必須の値を確かめる。 */
        public Created {
            Objects.requireNonNull(definitionId, "definitionId は必須です");
            Objects.requireNonNull(key, "key は必須です");
        }
    }

    /**
     * 1行を更新した・削除した。
     *
     * @param definitionId 今の定義の識別
     */
    record Written(DefinitionId definitionId) implements WriteResult {

        /** 必須の値を確かめる。 */
        public Written {
            Objects.requireNonNull(definitionId, "definitionId は必須です");
        }
    }

    /**
     * 拒否した（判定の順の ②〜⑩）。
     *
     * @param rejection 拒否
     * @param fieldErrors カラムに関わる誤り（400 と、カラムを示す 409 だけ。ほかは空。値は持たない）
     */
    record Rejected(WriteRejection rejection, List<BusinessDataFieldError> fieldErrors) implements WriteResult {

        /** 拒否が必須で、一覧を変更できないものにする。 */
        public Rejected {
            Objects.requireNonNull(rejection, "rejection は必須です");
            fieldErrors = List.copyOf(fieldErrors);
        }

        /**
         * 誤りの無い拒否を作る。
         *
         * @param rejection 拒否
         * @return 拒否
         */
        public static Rejected of(WriteRejection rejection) {
            return new Rejected(rejection, List.of());
        }
    }
}
