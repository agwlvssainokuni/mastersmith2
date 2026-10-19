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
import cherry.mastersmith.businessdata.domain.ReadRejection;
import java.util.List;
import java.util.Objects;

/**
 * 読み取りの API の想定内の拒否（定義・一覧と検索・詳細に共通。判定の順 BR1.1）。画面入出力の層が {@code switch} で場合を尽くして
 * 問題の種類に変える。
 *
 * @param rejection 拒否
 * @param fieldErrors カラムに関わる入力の誤り（400 だけ。ほかは空）
 * @param definitionChanged 要求の定義の識別が今と違うなら true（400 の応答にだけ載せる。BR9.2）
 */
public record ReadRejected(ReadRejection rejection, List<BusinessDataFieldError> fieldErrors, boolean definitionChanged)
        implements DefinitionResult, SearchResult, RowReadResult {

    /** 拒否が必須で、一覧を変更できないものにする。 */
    public ReadRejected {
        Objects.requireNonNull(rejection, "rejection は必須です");
        fieldErrors = List.copyOf(fieldErrors);
    }

    /**
     * 入力の誤りの無い拒否を作る。
     *
     * @param rejection 拒否
     * @return 拒否
     */
    public static ReadRejected of(ReadRejection rejection) {
        return new ReadRejected(rejection, List.of(), false);
    }
}
