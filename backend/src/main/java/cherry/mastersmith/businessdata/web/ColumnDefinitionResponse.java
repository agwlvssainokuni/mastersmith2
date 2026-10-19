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

import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.ColumnValidation;
import cherry.mastersmith.businessdata.domain.ValueType;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.ListFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 定義の応答のカラムの項目（契約 C2 の {@code ColumnDefinition}、BR2.8）。U1 は読み取りの項目を返す。検証の材料（{@code formPart} から
 * {@code hasPattern} まで）は {@link ColumnAccess#validation()} があるときだけ写す（U2 が埋める。U1 では出さない）。
 *
 * @param column カラムの名前
 * @param displayName 表示名
 * @param valueType 受け渡しの型
 * @param listFormat 一覧の書式（無ければ null）
 * @param width 一覧の幅（無ければ null）
 * @param editableOnCreate 作成で入力できるか
 * @param editableOnUpdate 更新で入力できるか
 * @param masked password のカラムか
 * @param formPart フォーム部品（検証の材料。無ければ出さない）
 * @param required 必須か（検証の材料。無ければ出さない）
 * @param nullable NULL を許すか（検証の材料。無ければ出さない）
 * @param minLength 文字数の下限（検証の材料。無ければ出さない）
 * @param maxLength 文字数の上限（検証の材料。無ければ出さない）
 * @param precision 精度（検証の材料。無ければ出さない）
 * @param scale 小数の桁（検証の材料。無ければ出さない）
 * @param min 値の下限（検証の材料。無ければ出さない）
 * @param max 値の上限（検証の材料。無ければ出さない）
 * @param choices 固定の選択肢（検証の材料。無ければ出さない）
 * @param hasPattern 正規表現の検証があるか（検証の材料。無ければ出さない）
 */
public record ColumnDefinitionResponse(
        String column,
        DisplayName displayName,
        ValueType valueType,
        ListFormat listFormat,
        Integer width,
        boolean editableOnCreate,
        boolean editableOnUpdate,
        boolean masked,
        @JsonInclude(JsonInclude.Include.NON_NULL) String formPart,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean required,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean nullable,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer minLength,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer maxLength,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer precision,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer scale,
        @JsonInclude(JsonInclude.Include.NON_NULL) String min,
        @JsonInclude(JsonInclude.Include.NON_NULL) String max,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<OptionResponse> choices,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean hasPattern) {

    /**
     * カラムの見せ方から作る。
     *
     * @param column カラム
     * @return 応答の項目
     */
    static ColumnDefinitionResponse from(ColumnAccess column) {
        ColumnValidation v = column.validation();
        return new ColumnDefinitionResponse(
                column.column(),
                column.displayName(),
                column.valueType(),
                column.listFormat(),
                column.width(),
                column.editableOnCreate(),
                column.editableOnUpdate(),
                column.masked(),
                v == null ? null : v.formPart().dslName(),
                v == null ? null : v.required(),
                v == null ? null : v.nullable(),
                v == null ? null : v.minLength(),
                v == null ? null : v.maxLength(),
                v == null ? null : v.precision(),
                v == null ? null : v.scale(),
                v == null ? null : v.min(),
                v == null ? null : v.max(),
                v == null
                        ? null
                        : v.choices().stream().map(OptionResponse::from).toList(),
                v == null ? null : v.hasPattern());
    }
}
