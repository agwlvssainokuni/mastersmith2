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
 * 定義の応答のカラムの項目（契約 C2 の {@code ColumnDefinition}、BR2.8）。U1 の読み取りの項目に、U2（Intent 261016-business-data-views の
 * B4、BR14.1・BR14.2）が検証の材料（{@code formPart} から {@code hasPattern} まで）を、すべてのカラムで省かずに足す。{@code required}・
 * {@code nullable} は必ず値を持ち、型に当たらない項目は {@code null} で出す（機能設計の承認の場の U2 R-07・U3 R-09 の決定）。値はサーバーの
 * 検証と同じ材料（{@link ColumnAccess#validation()}）から写すだけで、別に計算しない。
 *
 * @param column カラムの名前
 * @param displayName 表示名
 * @param valueType 受け渡しの型
 * @param listFormat 一覧の書式（無ければ null）
 * @param width 一覧の幅（無ければ null）
 * @param editableOnCreate 作成で入力できるか
 * @param editableOnUpdate 更新で入力できるか
 * @param masked password のカラムか
 * @param formPart フォーム部品（検証の材料。{@code lookup} は {@code text}）
 * @param required 必須か（書けないカラムは偽）
 * @param nullable NULL を許すか（DB の値のまま）
 * @param minLength 文字数の下限（STRING だけ。ほかは null）
 * @param maxLength 文字数の上限（STRING だけ。ほかは null）
 * @param precision 精度（DECIMAL だけ。ほかは null）
 * @param scale 小数の桁（DECIMAL だけ。ほかは null）
 * @param min 値の下限（数の型だけ。ほかは null）
 * @param max 値の上限（数の型だけ。ほかは null）
 * @param choices 固定の選択肢（select・radio だけ。ほかは空）
 * @param hasPattern 正規表現の検証があるか（式そのものは返さない）
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
        @JsonInclude(JsonInclude.Include.ALWAYS) String formPart,
        boolean required,
        boolean nullable,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer minLength,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer maxLength,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer precision,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer scale,
        @JsonInclude(JsonInclude.Include.ALWAYS) String min,
        @JsonInclude(JsonInclude.Include.ALWAYS) String max,
        List<OptionResponse> choices,
        boolean hasPattern) {

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
                v != null && v.required(),
                v != null && v.nullable(),
                v == null ? null : v.minLength(),
                v == null ? null : v.maxLength(),
                v == null ? null : v.precision(),
                v == null ? null : v.scale(),
                v == null ? null : v.min(),
                v == null ? null : v.max(),
                v == null
                        ? List.of()
                        : v.choices().stream().map(OptionResponse::from).toList(),
                v != null && v.hasPattern());
    }
}
