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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.service.WriteResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 作成の API の応答（201、Intent 261016-business-data-views の U2、BR7.2）。{@code key} は DETAIL が許され、作った行の主キーが分かるとき
 * だけ返す（無ければ項目を出さない。画面は一覧へ戻る。AC2.1.4）。文字列にするときは主キーを伏せる。
 *
 * @param definitionId 今の定義の識別
 * @param key 作った行の主キー（無ければ null）
 */
public record RowCreatedResponse(
        DefinitionIdBody definitionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> key) {

    /**
     * 結果から作る。
     *
     * @param created 作った結果
     * @return 応答
     */
    static RowCreatedResponse from(WriteResult.Created created) {
        Map<String, String> key = created.key().values();
        return new RowCreatedResponse(DefinitionIdBody.from(created.definitionId()), key.isEmpty() ? null : key);
    }

    /** 主キーのカラムの数だけを出す。 */
    @Override
    public String toString() {
        return "RowCreatedResponse[keyColumns=" + (key == null ? 0 : key.size()) + "]";
    }
}
