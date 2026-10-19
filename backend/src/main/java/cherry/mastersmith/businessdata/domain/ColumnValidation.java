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
package cherry.mastersmith.businessdata.domain;

import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionItem;
import java.util.List;
import java.util.Objects;

/**
 * 入力の検証の材料（契約 C2 の {@code ColumnDefinition} の検証の項目、entities.md の ColumnValidation、BR2.8）。持ち主は U2 で、
 * U1 は型と置き場だけを用意し、値を入れない（{@link ColumnAccess#validation()} は null のまま）。
 *
 * @param formPart フォーム部品
 * @param required 必須なら true
 * @param nullable NULL を許すなら true
 * @param minLength 文字数の下限（無ければ null）
 * @param maxLength 文字数の上限（無ければ null）
 * @param precision 精度（無ければ null）
 * @param scale 小数の桁（無ければ null）
 * @param min 値の下限（文字。無ければ null）
 * @param max 値の上限（文字。無ければ null）
 * @param choices 固定の選択肢（無ければ空）
 * @param hasPattern 正規表現の検証があれば true（式そのものは返さない）
 */
public record ColumnValidation(
        FormPart formPart,
        boolean required,
        boolean nullable,
        Integer minLength,
        Integer maxLength,
        Integer precision,
        Integer scale,
        String min,
        String max,
        List<OptionItem> choices,
        boolean hasPattern) {

    /** フォーム部品が必須で、選択肢の一覧を変更できないものにする。 */
    public ColumnValidation {
        Objects.requireNonNull(formPart, "formPart は必須です");
        choices = List.copyOf(choices == null ? List.of() : choices);
    }
}
