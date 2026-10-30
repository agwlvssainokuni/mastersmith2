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

import cherry.mastersmith.businessdata.domain.ReferenceField;
import cherry.mastersmith.businessdata.domain.ReferenceStatus;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 定義の応答のカラムの {@code reference}（Intent 261029-reference-lookup-cleanup の U1、契約 C2 の ReferenceField）。選択肢は
 * REFERENCE で AVAILABLE のときだけ、ピッカーの情報は LOOKUP で AVAILABLE のときだけ持ち、ほかは載せない（BR3.6）。文字列にする
 * ときは選択肢の数だけを出す（BR10.1）。
 *
 * @param kind 種類（{@code REFERENCE} か {@code LOOKUP}）
 * @param status 状態
 * @param options 選択肢（REFERENCE で AVAILABLE のときだけ）
 * @param picker ピッカーの情報（LOOKUP で AVAILABLE のときだけ）
 */
public record ReferenceFieldResponse(
        OptionSourceKind kind,
        ReferenceStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ReferenceOptionResponse> options,
        @JsonInclude(JsonInclude.Include.NON_NULL) PickerInfoResponse picker) {

    /**
     * 参照の項目から作る。
     *
     * @param field 参照の項目
     * @return 応答
     */
    static ReferenceFieldResponse from(ReferenceField field) {
        boolean choices = field.kind() == OptionSourceKind.REFERENCE && field.status() == ReferenceStatus.AVAILABLE;
        return new ReferenceFieldResponse(
                field.kind(),
                field.status(),
                choices
                        ? field.options().stream()
                                .map(ReferenceOptionResponse::from)
                                .toList()
                        : null,
                field.picker() == null ? null : PickerInfoResponse.from(field.picker()));
    }

    /** 種類・状態・選択肢の数だけを出す（値とラベルを出さない。BR10.1）。 */
    @Override
    public String toString() {
        return "ReferenceFieldResponse[kind=" + kind + ", status=" + status + ", options="
                + (options == null ? "none" : options.size()) + ", picker=" + (picker != null) + "]";
    }
}
