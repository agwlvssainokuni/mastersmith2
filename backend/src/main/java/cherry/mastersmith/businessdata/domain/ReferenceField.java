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

import cherry.mastersmith.dsl.domain.OptionSourceKind;
import java.util.List;
import java.util.Objects;

/**
 * 定義の応答のカラムの項目に足す参照の情報（Intent 261029-reference-lookup-cleanup の U1、契約 C2 の ReferenceField、entities.md の
 * ReferenceField）。用途が VIEW のときと参照でないカラムでは作らない。
 *
 * <p>状態が {@link ReferenceStatus#AVAILABLE} でないときは選択肢もピッカーの情報も持たない（BR3.6）。REFERENCE の AVAILABLE は
 * 選択肢（0〜200）を、LOOKUP の AVAILABLE はピッカーの情報を持つ。文字列にするときは選択肢の数だけを出す（BR10.1）。
 *
 * <p>ラベルの列の受け渡しの型（{@code labelValueType}）は、AVAILABLE のときだけ持つ（Intent 261102-timezone-ui-polish の U4、
 * functional-spec.md D30・security-design.md 2.1）。型の名前だけで、値・ラベルを足さない。参照先の権限を欠く利用者に参照先のカラム
 * の型を明かさないため、AVAILABLE でない項目は持たない。
 *
 * @param kind 種類（{@code REFERENCE} か {@code LOOKUP}）
 * @param status 状態
 * @param options 選択肢（REFERENCE で AVAILABLE のときだけ。ほかは空）
 * @param picker ピッカーの情報（LOOKUP で AVAILABLE のときだけ。ほかは null）
 * @param labelValueType ラベルの列の受け渡しの型（AVAILABLE のときだけ。ほかと、型が決まらないときは null）
 */
public record ReferenceField(
        OptionSourceKind kind,
        ReferenceStatus status,
        List<ReferenceOption> options,
        PickerInfo picker,
        ValueType labelValueType) {

    /** 選択肢の数の上限（BR4.3）。 */
    public static final int MAX_OPTIONS = 200;

    /** 種類・状態と、持つものの組が食い違わないことを確かめる（BR3.6・BR4.3・D30）。 */
    public ReferenceField {
        Objects.requireNonNull(kind, "kind は必須です");
        Objects.requireNonNull(status, "status は必須です");
        if (kind == OptionSourceKind.FIXED) {
            throw new IllegalArgumentException("参照の項目は REFERENCE か LOOKUP です");
        }
        options = List.copyOf(options);
        boolean availableReference = status == ReferenceStatus.AVAILABLE && kind == OptionSourceKind.REFERENCE;
        boolean availableLookup = status == ReferenceStatus.AVAILABLE && kind == OptionSourceKind.LOOKUP;
        if (!availableReference && !options.isEmpty()) {
            throw new IllegalArgumentException("選択肢は REFERENCE で AVAILABLE のときだけです");
        }
        if (options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("選択肢は 200 までです");
        }
        if (availableLookup != (picker != null)) {
            throw new IllegalArgumentException("ピッカーの情報は LOOKUP で AVAILABLE のときだけ持ちます");
        }
        if (kind == OptionSourceKind.LOOKUP && status == ReferenceStatus.TOO_MANY) {
            throw new IllegalArgumentException("LOOKUP に TOO_MANY はありません");
        }
        if (status != ReferenceStatus.AVAILABLE && labelValueType != null) {
            throw new IllegalArgumentException("ラベルの列の型は AVAILABLE のときだけ持ちます");
        }
    }

    /**
     * 読めない（または用途で入力できない）項目を作る（ラベルの列の型を持たない）。
     *
     * @param kind 種類
     * @return 項目
     */
    public static ReferenceField noPermission(OptionSourceKind kind) {
        return new ReferenceField(kind, ReferenceStatus.NO_PERMISSION, List.of(), null, null);
    }

    /**
     * 候補が多すぎる REFERENCE の項目を作る（ラベルの列の型を持たない）。
     *
     * @return 項目
     */
    public static ReferenceField tooMany() {
        return new ReferenceField(OptionSourceKind.REFERENCE, ReferenceStatus.TOO_MANY, List.of(), null, null);
    }

    /**
     * 選択肢を持つ REFERENCE の項目を作る。
     *
     * @param options 選択肢（同じ値を落とした後。0〜200）
     * @param labelValueType ラベルの列の受け渡しの型（{@link ReferencePolicy#labelValueTypeOf} で決めたもの。決まらなければ null）
     * @return 項目
     */
    public static ReferenceField choices(List<ReferenceOption> options, ValueType labelValueType) {
        return new ReferenceField(OptionSourceKind.REFERENCE, ReferenceStatus.AVAILABLE, options, null, labelValueType);
    }

    /**
     * ピッカーの情報を持つ LOOKUP の項目を作る。
     *
     * @param picker ピッカーの情報
     * @param labelValueType ラベルの列の受け渡しの型（{@link ReferencePolicy#labelValueTypeOf} で決めたもの。決まらなければ null）
     * @return 項目
     */
    public static ReferenceField lookup(PickerInfo picker, ValueType labelValueType) {
        return new ReferenceField(
                OptionSourceKind.LOOKUP,
                ReferenceStatus.AVAILABLE,
                List.of(),
                Objects.requireNonNull(picker),
                labelValueType);
    }

    /** 種類・状態・選択肢の数・ラベルの列の型の名前だけを出す（値とラベルを出さない。BR10.1・D30）。 */
    @Override
    public String toString() {
        return "ReferenceField[kind=" + kind + ", status=" + status + ", options=" + options.size() + ", picker="
                + (picker != null) + ", labelValueType=" + labelValueType + "]";
    }
}
