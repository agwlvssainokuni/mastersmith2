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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 作成・更新・削除の要求の本文を確かめる純粋な関数（Intent 261016-business-data-views の U2、BR2.1〜BR2.4・BR3.1〜BR3.8・BR4.1・
 * BR4.2、NFR4.9、logical-components.md の L4）。時計は {@link DeadlinePatternMatcher} に注入する。
 *
 * <p>サーバーの結果を正とし、画面の検証を通ったかを前提にしない。誤りはカラムごとに最初の1つを、まとめて返す。長さは照合の前に当てる。
 * 値は型として読み、型つきの値（{@link TypedValue}）にする（型の対応は {@link ColumnValueReader} の1か所）。文字列にするときに値を
 * 出さない型だけを返す。
 */
public final class WriteRequestValidator {

    /** 整数の文字の形（型の範囲の外を {@link BusinessDataFieldErrorReason#OUT_OF_RANGE} と見分けるため）。 */
    private static final Pattern INTEGER_TEXT = Pattern.compile("[+-]?[0-9]+");

    private WriteRequestValidator() {}

    /**
     * 要求を確かめる。
     *
     * @param view 判定の材料（その操作が許されるもの）
     * @param operation 操作
     * @param key 本文の主キー（更新・削除）
     * @param values 本文の値（作成・更新）
     * @param reader 文字をカラムの型として読む口
     * @param matcher 締め切りつきの正規表現の照合
     * @return 確かめの結果
     */
    public static WriteValidation validate(
            TableAccessView view,
            WriteOperation operation,
            KeyInput key,
            WriteValues values,
            ColumnValueReader reader,
            DeadlinePatternMatcher matcher) {
        return validate(view, operation, key, values, reader, matcher, Hold.NONE);
    }

    /**
     * 作成の必須の参照項目の空の誤りを保留して要求を確かめる（Intent 261029-reference-lookup-cleanup の U2、BR3.6）。
     *
     * <p>保留するのは、作成で {@link Hold#columns()} のカラムが空（本文に無い・null・空文字列）のときに出る {@code REQUIRED}・
     * {@code NOT_NULL}・{@code INVALID_VALUE} の誤りだけ（空でない不正な値の誤りは保留しない）。ほかのカラムに誤りがあれば、
     * {@link Hold#droppedOnInvalid()} のカラムの保留した誤りを落とし、残りの保留した誤りを元の位置に並べて
     * {@link WriteValidation.Invalid} を返す。保留した誤りしか無ければ、{@link WriteValidation.Valid#held()} に入れて返す。更新・削除では
     * 保留しない。
     *
     * @param view 判定の材料（その操作が許されるもの）
     * @param operation 操作
     * @param key 本文の主キー（更新・削除）
     * @param values 本文の値（作成・更新）
     * @param reader 文字をカラムの型として読む口
     * @param matcher 締め切りつきの正規表現の照合
     * @param hold 保留するカラム
     * @return 確かめの結果
     */
    public static WriteValidation validate(
            TableAccessView view,
            WriteOperation operation,
            KeyInput key,
            WriteValues values,
            ColumnValueReader reader,
            DeadlinePatternMatcher matcher,
            Hold hold) {
        Objects.requireNonNull(view, "view は必須です");
        Objects.requireNonNull(operation, "operation は必須です");
        Objects.requireNonNull(reader, "reader は必須です");
        Objects.requireNonNull(matcher, "matcher は必須です");
        Objects.requireNonNull(hold, "hold は必須です");
        Hold active = operation == WriteOperation.CREATE ? hold : Hold.NONE;
        List<BusinessDataFieldError> errors = new ArrayList<>();
        List<BusinessDataFieldError> heldErrors = new ArrayList<>();
        RowKey rowKey = null;
        if (operation != WriteOperation.CREATE) {
            KeyValidation keyValidation = SearchRequestValidator.validateKey(view, key, reader);
            switch (keyValidation) {
                case KeyValidation.Valid valid -> rowKey = valid.key();
                case KeyValidation.Invalid invalid -> errors.addAll(invalid.errors());
            }
        }
        List<TypedValue> typed = new ArrayList<>();
        if (operation == WriteOperation.UPDATE && values.values().isEmpty()) {
            errors.add(new BusinessDataFieldError("", BusinessDataFieldErrorReason.NO_CHANGE));
        }
        if (operation != WriteOperation.DELETE) {
            DeadlinePatternMatcher.Budget budget = matcher.budget();
            String dslHash = view.definitionId().dslHash();
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(values.values()).entrySet()) {
                String name = entry.getKey();
                Optional<ColumnAccess> column = name == null ? Optional.empty() : view.column(name);
                Optional<ColumnWriteRules> rules = name == null ? Optional.empty() : view.writeRules(name);
                if (column.isEmpty() || rules.isEmpty() || !editable(column.get(), operation)) {
                    // このテーブルで読めるカラムに無い名前（DSL に無い名前・読めないカラム）は、要求の名前をそのまま返さない
                    // （長い名前や引用符を含む名前が応答に載らないよう、空の field にする）。DSL にある名前は従来どおり返す。
                    errors.add(new BusinessDataFieldError(
                            column.isEmpty() ? "" : name, BusinessDataFieldErrorReason.NOT_ALLOWED));
                    continue;
                }
                switch (check(column.get(), rules.get(), entry.getValue(), reader, matcher, dslHash, budget)) {
                    case Checked.Ok ok -> typed.add(ok.value());
                    case Checked.Error error -> {
                        errors.add(error.error());
                        if (active.holds(name, entry.getValue(), error.error().reason())) {
                            heldErrors.add(error.error());
                        }
                    }
                }
            }
        }
        if (operation == WriteOperation.CREATE) {
            for (ColumnAccess column : view.columns()) {
                Optional<ColumnWriteRules> rules = view.writeRules(column.column());
                if (column.editableOnCreate()
                        && rules.isPresent()
                        && rules.get().required()
                        && !values.values().containsKey(column.column())) {
                    // DB 由来・人が足した必須のどちらも、作成で送られなければ誤りにする（BR3.3、機能設計の X5）。
                    BusinessDataFieldError error =
                            new BusinessDataFieldError(column.column(), BusinessDataFieldErrorReason.REQUIRED);
                    errors.add(error);
                    if (active.columns().contains(column.column())) {
                        heldErrors.add(error);
                    }
                }
            }
        }
        if (errors.size() > heldErrors.size()) {
            // ほかに誤りがあれば、読めない参照先のカラムの保留した誤りを落とし、残りを元の位置のまま返す（BR3.6）。
            List<BusinessDataFieldError> shown = new ArrayList<>();
            for (BusinessDataFieldError error : errors) {
                boolean dropped = heldErrors.stream().anyMatch(h -> h == error)
                        && active.droppedOnInvalid().contains(error.field());
                if (!dropped) {
                    shown.add(error);
                }
            }
            return new WriteValidation.Invalid(shown);
        }
        return new WriteValidation.Valid(typed, rowKey, heldErrors);
    }

    /**
     * 作成の必須の参照項目の空の誤りを保留するカラム（Intent 261029-reference-lookup-cleanup の U2、BR3.6）。
     *
     * @param columns 保留するカラム（作成の必須の参照項目）
     * @param droppedOnInvalid ほかに誤りがあるときに保留した誤りを落とすカラム（参照先が読めない {@code NO_PERMISSION} のもの）
     */
    public record Hold(Set<String> columns, Set<String> droppedOnInvalid) {

        /** 何も保留しない。 */
        public static final Hold NONE = new Hold(Set.of(), Set.of());

        /** 集合を変更できないものにする。 */
        public Hold {
            columns = Set.copyOf(Objects.requireNonNull(columns, "columns は必須です"));
            droppedOnInvalid = Set.copyOf(Objects.requireNonNull(droppedOnInvalid, "droppedOnInvalid は必須です"));
        }

        /** 本文の値が空（null・空文字列）のときに出た、空の誤りだけを保留する。 */
        boolean holds(String column, String text, BusinessDataFieldErrorReason reason) {
            boolean empty = text == null || text.isEmpty();
            return columns.contains(column)
                    && empty
                    && (reason == BusinessDataFieldErrorReason.REQUIRED
                            || reason == BusinessDataFieldErrorReason.NOT_NULL
                            || reason == BusinessDataFieldErrorReason.INVALID_VALUE);
        }
    }

    private static boolean editable(ColumnAccess column, WriteOperation operation) {
        return operation == WriteOperation.CREATE ? column.editableOnCreate() : column.editableOnUpdate();
    }

    /** カラム1つを確かめる（最初に当たった誤りだけを返す）。 */
    private static Checked check(
            ColumnAccess column,
            ColumnWriteRules rules,
            String text,
            ColumnValueReader reader,
            DeadlinePatternMatcher matcher,
            String dslHash,
            DeadlinePatternMatcher.Budget budget) {
        String name = column.column();
        ValueType type = rules.valueType();
        boolean empty = text == null || text.isEmpty();
        if (empty && rules.required()) {
            return Checked.error(name, BusinessDataFieldErrorReason.REQUIRED);
        }
        if (text == null) {
            return rules.nullable()
                    ? new Checked.Ok(new TypedValue(name, new BoundValue(type, null)))
                    : Checked.error(name, BusinessDataFieldErrorReason.NOT_NULL);
        }
        if (type == ValueType.BINARY || type == ValueType.OTHER) {
            return Checked.error(name, BusinessDataFieldErrorReason.NOT_ALLOWED);
        }
        if (text.isEmpty() && type != ValueType.STRING) {
            return Checked.error(name, BusinessDataFieldErrorReason.INVALID_VALUE);
        }
        if ((rules.formPart() == FormPart.CHECKBOX || rules.formPart() == FormPart.SWITCH)
                && !text.equals("true")
                && !text.equals("false")) {
            return Checked.error(name, BusinessDataFieldErrorReason.INVALID_VALUE);
        }
        if (type == ValueType.STRING) {
            Optional<BusinessDataFieldError> stringError = checkString(rules, name, text);
            if (stringError.isPresent()) {
                return new Checked.Error(stringError.get());
            }
        }
        if (!rules.choices().isEmpty()
                && rules.choices().stream().map(OptionItem::value).noneMatch(text::equals)) {
            return Checked.error(name, BusinessDataFieldErrorReason.NOT_A_CHOICE);
        }
        if (type == ValueType.STRING && rules.pattern() != null && !text.isEmpty()) {
            switch (matcher.match(dslHash, rules.pattern(), text, budget)) {
                case MATCH -> {}
                case MISMATCH -> {
                    return new Checked.Error(new BusinessDataFieldError(
                            name, BusinessDataFieldErrorReason.PATTERN_MISMATCH, rules.patternMessage()));
                }
                case TIMEOUT -> {
                    return Checked.error(name, BusinessDataFieldErrorReason.PATTERN_TIMEOUT);
                }
            }
        }
        Optional<BoundValue> read = type == ValueType.STRING
                ? Optional.of(new BoundValue(ValueType.STRING, text))
                : reader.read(column, text);
        if (read.isEmpty()) {
            boolean integerOutOfRange = (type == ValueType.INTEGER || type == ValueType.BIG_INTEGER)
                    && INTEGER_TEXT.matcher(text).matches();
            return Checked.error(
                    name,
                    integerOutOfRange
                            ? BusinessDataFieldErrorReason.OUT_OF_RANGE
                            : BusinessDataFieldErrorReason.INVALID_VALUE);
        }
        BoundValue value = read.get();
        Optional<BusinessDataFieldErrorReason> range = checkRange(rules, value);
        if (range.isPresent()) {
            return Checked.error(name, range.get());
        }
        return new Checked.Ok(new TypedValue(name, value));
    }

    /** 文字数（コードポイント）の上限と下限（BR3.4）。空の文字には下限を当てない。 */
    private static Optional<BusinessDataFieldError> checkString(ColumnWriteRules rules, String name, String text) {
        int length = text.codePointCount(0, text.length());
        if (rules.maxLength() != null && length > rules.maxLength()) {
            return Optional.of(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.TOO_LONG));
        }
        if (rules.minLength() != null && length > 0 && length < rules.minLength()) {
            return Optional.of(new BusinessDataFieldError(name, BusinessDataFieldErrorReason.TOO_SHORT));
        }
        return Optional.empty();
    }

    /** 桁・範囲・秒の端数（BR3.5、NFR 設計 Q6: A）。 */
    private static Optional<BusinessDataFieldErrorReason> checkRange(ColumnWriteRules rules, BoundValue value) {
        Object raw = value.value();
        switch (value.valueType()) {
            case INTEGER, BIG_INTEGER, DECIMAL -> {
                BigDecimal number = raw instanceof Long l ? BigDecimal.valueOf(l) : (BigDecimal) raw;
                if (value.valueType() == ValueType.DECIMAL && tooManyDigits(rules, number)) {
                    return Optional.of(BusinessDataFieldErrorReason.TOO_MANY_DIGITS);
                }
                if (rules.min() != null && number.compareTo(new BigDecimal(rules.min())) < 0) {
                    return Optional.of(BusinessDataFieldErrorReason.OUT_OF_RANGE);
                }
                if (rules.max() != null && number.compareTo(new BigDecimal(rules.max())) > 0) {
                    return Optional.of(BusinessDataFieldErrorReason.OUT_OF_RANGE);
                }
            }
            case DATETIME, DATETIME_TZ, TIME, TIME_TZ -> {
                if (rules.fractionDigits() != null && fractionDigits(raw) > rules.fractionDigits()) {
                    return Optional.of(BusinessDataFieldErrorReason.TOO_MANY_DIGITS);
                }
            }
            default -> {}
        }
        return Optional.empty();
    }

    /** DECIMAL(p,s) の桁（小数の桁が s を超える・整数部が p−s を超える。丸めない）。 */
    private static boolean tooManyDigits(ColumnWriteRules rules, BigDecimal number) {
        if (rules.precision() == null) {
            return false;
        }
        int scale = rules.scale() == null ? 0 : rules.scale();
        BigDecimal stripped = number.stripTrailingZeros();
        int fraction = Math.max(0, stripped.scale());
        int integer = Math.max(0, stripped.precision() - stripped.scale());
        return fraction > scale || integer > rules.precision() - scale;
    }

    /** 秒の端数の要る桁の数（末尾の 0 を数えない）。 */
    private static int fractionDigits(Object raw) {
        int nanos =
                switch (raw) {
                    case LocalDateTime v -> v.getNano();
                    case OffsetDateTime v -> v.getNano();
                    case LocalTime v -> v.getNano();
                    case OffsetTime v -> v.getNano();
                    default -> 0;
                };
        if (nanos == 0) {
            return 0;
        }
        int digits = 9;
        while (nanos % 10 == 0) {
            nanos /= 10;
            digits--;
        }
        return digits;
    }

    /** カラム1つの確かめの結果。 */
    private sealed interface Checked {

        /** 通った。 */
        record Ok(TypedValue value) implements Checked {}

        /** 誤り。 */
        record Error(BusinessDataFieldError error) implements Checked {}

        static Checked error(String field, BusinessDataFieldErrorReason reason) {
            return new Error(new BusinessDataFieldError(field, reason));
        }
    }
}
