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

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dsl.domain.ValidationOrigin;
import java.util.List;
import java.util.Objects;

/**
 * カラム1つの検証の材料（サーバーの中だけで使う全体。Intent 261016-business-data-views の U2、entities.md の ColumnWriteRules、BR3・
 * BR4・BR5・BR14.2）。写しの DSL と DB の型から {@link ColumnWriteRulesFactory} だけが作り、サーバーの検証と定義の API の項目
 * （{@link ColumnValidation}）の両方の元にする。
 *
 * <p>型に合わない検証は持たない（BR3.6 の表。{@code minLength}・{@code maxLength}・{@code pattern} は STRING だけ、{@code min}・
 * {@code max} は数の型だけ、{@code precision}・{@code scale} は DECIMAL だけ、{@code fractionDigits} は日時・時刻の型だけ）。
 *
 * @param column カラムの名前
 * @param valueType 受け渡しの型
 * @param dbTypeName DSL の型の名前
 * @param formPart フォーム部品（{@code lookup} は {@code text}）
 * @param nullable NULL を許すなら true（DSL の {@code dbType.nullable}）
 * @param required 必須なら true（DSL の {@code required}）
 * @param requiredOrigin 必須の出どころ（必須でなければ null。定義の API には出さない）
 * @param minLength 文字数の下限（コードポイント。無ければ null）
 * @param maxLength 文字数の上限（コードポイント。DSL・DB の長さと 10,000 字の小さい方。STRING 以外は null）
 * @param precision 全体の桁（DECIMAL だけ。無ければ null）
 * @param scale 小数の桁（DECIMAL だけ。無ければ null）
 * @param fractionDigits 秒の端数の桁の上限（日時・時刻の型だけ。無ければ null）
 * @param min 値の下限（数の型だけ。10進の文字。無ければ null）
 * @param max 値の上限（数の型だけ。10進の文字。無ければ null）
 * @param pattern 正規表現（STRING だけ。式は応答に出さない。無ければ null）
 * @param patternMessage 正規表現に合わないときの DSL の文（無ければ null）
 * @param unique DSL で unique なら true（定義の API には出さない）
 * @param choices 固定の選択肢（select・radio の FIXED だけ。ほかは空）
 * @param masked フォーム部品が password なら true
 */
public record ColumnWriteRules(
        String column,
        ValueType valueType,
        String dbTypeName,
        FormPart formPart,
        boolean nullable,
        boolean required,
        ValidationOrigin requiredOrigin,
        Integer minLength,
        Integer maxLength,
        Integer precision,
        Integer scale,
        Integer fractionDigits,
        String min,
        String max,
        String pattern,
        DisplayName patternMessage,
        boolean unique,
        List<OptionItem> choices,
        boolean masked) {

    /** 必須の値を確かめ、選択肢の一覧を変更できないものにする。 */
    public ColumnWriteRules {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(valueType, "valueType は必須です");
        Objects.requireNonNull(dbTypeName, "dbTypeName は必須です");
        Objects.requireNonNull(formPart, "formPart は必須です");
        if (formPart == FormPart.LOOKUP) {
            throw new IllegalArgumentException("lookup は text として扱います");
        }
        choices = List.copyOf(choices == null ? List.of() : choices);
    }

    /** カラムの名前と型と、検証の有無だけを出す（式・選択肢を出さない）。 */
    @Override
    public String toString() {
        return "ColumnWriteRules[column=" + column + ", valueType=" + valueType + ", required=" + required + ", unique="
                + unique + ", hasPattern=" + (pattern != null) + "]";
    }
}
