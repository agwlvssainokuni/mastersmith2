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

import cherry.mastersmith.businessdata.domain.PickerInfo;
import java.util.List;

/**
 * LOOKUP のピッカーの情報の応答（Intent 261029-reference-lookup-cleanup の U1、契約 C2、BR5.1）。参照先のスキーマ名・テーブル名を
 * 持たない（BR3.6）。
 *
 * @param searchFields 使える検索の条件
 * @param listColumns 一覧の列
 */
public record PickerInfoResponse(List<PickerFieldResponse> searchFields, List<PickerFieldResponse> listColumns) {

    /**
     * ピッカーの情報から作る。
     *
     * @param picker ピッカーの情報
     * @return 応答
     */
    static PickerInfoResponse from(PickerInfo picker) {
        return new PickerInfoResponse(
                picker.searchFields().stream().map(PickerFieldResponse::from).toList(),
                picker.listColumns().stream().map(PickerFieldResponse::from).toList());
    }
}
