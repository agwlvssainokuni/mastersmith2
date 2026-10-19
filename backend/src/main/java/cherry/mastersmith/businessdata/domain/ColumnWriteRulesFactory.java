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

import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.FormPart;
import cherry.mastersmith.dsl.domain.OptionItem;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import cherry.mastersmith.dsl.domain.Validation;
import cherry.mastersmith.dsl.domain.ValidationType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 写しの DSL のカラムと DB の型から {@link ColumnWriteRules} を作る純粋な関数（Intent 261016-business-data-views の U2、BR3.6・
 * BR14.2、entities.md の ColumnWriteRules）。サーバーの検証と定義の API の項目は、この関数の結果だけから作る（別に計算しない）。
 */
public final class ColumnWriteRulesFactory {

    /** 書き込みの値1つの文字数の上限（コードポイント。NFR4.9）。 */
    public static final int MAX_VALUE_LENGTH = 10_000;

    private ColumnWriteRulesFactory() {}

    /**
     * 検証の材料を作る。
     *
     * @param column 写しの DSL のカラム
     * @param kind DB の種類と DSL の型から決めた受け渡しの型
     * @return 検証の材料
     */
    public static ColumnWriteRules create(DslColumn column, ColumnKind kind) {
        ValueType type = kind.valueType();
        FormPart formPart = column.formPart() == FormPart.LOOKUP ? FormPart.TEXT : column.formPart();
        Optional<Validation> required = first(column, ValidationType.REQUIRED);
        boolean string = type == ValueType.STRING;
        boolean numeric = type == ValueType.INTEGER || type == ValueType.BIG_INTEGER || type == ValueType.DECIMAL;
        boolean decimal = type == ValueType.DECIMAL;
        boolean temporal = type == ValueType.DATETIME
                || type == ValueType.DATETIME_TZ
                || type == ValueType.TIME
                || type == ValueType.TIME_TZ;
        Optional<Validation> pattern = string ? first(column, ValidationType.PATTERN) : Optional.empty();
        return new ColumnWriteRules(
                column.name(),
                type,
                column.dbType().name(),
                formPart,
                column.dbType().nullable(),
                required.isPresent(),
                required.map(Validation::origin).orElse(null),
                string
                        ? number(column, ValidationType.MIN_LENGTH)
                                .map(BigDecimal::intValue)
                                .orElse(null)
                        : null,
                string ? maxLength(column) : null,
                decimal ? column.dbType().precision() : null,
                decimal ? column.dbType().scale() : null,
                temporal ? kind.fractionDigits() : null,
                numeric
                        ? number(column, ValidationType.MIN)
                                .map(BigDecimal::toPlainString)
                                .orElse(null)
                        : null,
                numeric
                        ? number(column, ValidationType.MAX)
                                .map(BigDecimal::toPlainString)
                                .orElse(null)
                        : null,
                pattern.map(Validation::text).orElse(null),
                pattern.map(Validation::message).orElse(null),
                first(column, ValidationType.UNIQUE).isPresent(),
                choices(column, formPart),
                formPart == FormPart.PASSWORD);
    }

    /** 文字数の上限（DSL の maxLength・DB の長さ・10,000 字の小さい方。NFR4.9）。 */
    private static Integer maxLength(DslColumn column) {
        int limit = MAX_VALUE_LENGTH;
        Optional<BigDecimal> dsl = number(column, ValidationType.MAX_LENGTH);
        if (dsl.isPresent()) {
            limit = Math.min(
                    limit, dsl.get().min(BigDecimal.valueOf(MAX_VALUE_LENGTH)).intValue());
        }
        if (column.dbType().length() != null && column.dbType().length() > 0) {
            limit = Math.min(limit, column.dbType().length());
        }
        return limit;
    }

    /** select・radio の FIXED の選択肢（REFERENCE・LOOKUP は text として扱い、選択肢と照らさない。BR3.7）。 */
    private static List<OptionItem> choices(DslColumn column, FormPart formPart) {
        if ((formPart != FormPart.SELECT && formPart != FormPart.RADIO)
                || column.options() == null
                || column.options().source() != OptionSourceKind.FIXED) {
            return List.of();
        }
        return column.options().items();
    }

    private static Optional<Validation> first(DslColumn column, ValidationType type) {
        return column.validations().stream().filter(v -> v.type() == type).findFirst();
    }

    private static Optional<BigDecimal> number(DslColumn column, ValidationType type) {
        return first(column, type).map(Validation::number);
    }
}
