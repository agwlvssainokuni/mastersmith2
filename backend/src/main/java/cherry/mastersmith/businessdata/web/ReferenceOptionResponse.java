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

import cherry.mastersmith.businessdata.domain.ReferenceOption;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * REFERENCE の選択肢1つの応答（Intent 261029-reference-lookup-cleanup の U1、契約 C2）。文字列にするときは値とラベルを伏せる
 * （BR10.1）。
 *
 * @param value 値（切らない）
 * @param label ラベル（NULL・バイナリ・password は null）
 */
public record ReferenceOptionResponse(
        String value,
        @JsonInclude(JsonInclude.Include.ALWAYS) String label) {

    /**
     * 選択肢から作る。
     *
     * @param option 選択肢
     * @return 応答
     */
    static ReferenceOptionResponse from(ReferenceOption option) {
        return new ReferenceOptionResponse(option.value(), option.label());
    }

    /** 値とラベルを伏せる（BR10.1）。 */
    @Override
    public String toString() {
        return "ReferenceOptionResponse[value=***, label=" + (label == null ? "null" : "***") + "]";
    }
}
