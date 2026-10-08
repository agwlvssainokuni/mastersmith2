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
package cherry.mastersmith.role.service;

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionLevel;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionTargetName;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.user.domain.FieldErrorReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 権限の保存と消す操作の入力の判定（BR4.1・BR4.3・BR4.4・BR4.7、BR2.3）。DB にも時計にも触れない。誤りは最初に当たった項目の名前と
 * 理由だけを返し、入れた値を持たない（400 {@code VALIDATION_FAILED}、監査なし）。
 *
 * <ul>
 *   <li>保存の {@code scope} はスキーマ（{@code tableName} が null）かテーブル。スキーマの範囲ではそのスキーマの対象だけ、テーブルの
 *       範囲ではそのテーブルとそのカラムの対象だけを受ける（画面の1つの表の分。計画の 7.2）
 *   <li>名前は 1〜128 コードポイント・制御文字なし（{@link PermissionTargetName}）。下位の名前は null か名前。カラムはテーブルの下だけ
 *   <li>主権限は null・{@code NONE}・{@code READ}・{@code FULL}。カラムに CREATE・DELETE があれば誤り
 *   <li>同じ対象が2回あれば誤り。{@code entries}・{@code targets} は1件以上
 * </ul>
 */
public final class PermissionInputs {

    private PermissionInputs() {}

    /**
     * 保存の1件の入力（要求の値のまま）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（スキーマの対象は null）
     * @param columnName カラムの名前（スキーマ・テーブルの対象は null）
     * @param main 主権限の名前（null は設定なし）
     * @param create CREATE（null は設定なし）
     * @param delete DELETE（null は設定なし）
     */
    public record EntryInput(
            String schemaName, String tableName, String columnName, String main, Boolean create, Boolean delete) {

        /** 値を出さない（D-5）。 */
        @Override
        public String toString() {
            return "EntryInput";
        }
    }

    /**
     * 消す操作の1件の入力（要求の値のまま）。
     *
     * @param schemaName スキーマの名前
     * @param tableName テーブルの名前（スキーマの対象は null）
     * @param columnName カラムの名前（スキーマ・テーブルの対象は null）
     */
    public record TargetInput(String schemaName, String tableName, String columnName) {

        /** 値を出さない（D-5）。 */
        @Override
        public String toString() {
            return "TargetInput";
        }
    }

    /**
     * 判定した保存の要求。
     *
     * @param scope 範囲（スキーマかテーブルの対象）
     * @param entries 対象ごとの要求の値（要求の順）
     */
    public record SaveCommand(PermissionTarget scope, Map<PermissionTarget, PermissionValues> entries) {

        /** 値を写して持つ。 */
        public SaveCommand {
            Objects.requireNonNull(scope, "scope");
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        /** 件数だけを出す（D-5）。 */
        @Override
        public String toString() {
            return "SaveCommand[level=" + scope.level() + ", entries=" + entries.size() + "]";
        }
    }

    /**
     * 判定した消す操作の要求。
     *
     * @param targets 対象（要求の順、重なりなし）
     */
    public record ClearCommand(List<PermissionTarget> targets) {

        /** 値を写して持つ。 */
        public ClearCommand {
            targets = List.copyOf(targets);
        }

        /** 件数だけを出す（D-5）。 */
        @Override
        public String toString() {
            return "ClearCommand[targets=" + targets.size() + "]";
        }
    }

    /**
     * 判定の結果。
     *
     * @param <T> 正しいときの値の型
     */
    public sealed interface Validation<T> permits Validation.Valid, Validation.Invalid {

        /**
         * 正しい。
         *
         * @param <T> 値の型
         * @param value 値
         */
        record Valid<T>(T value) implements Validation<T> {}

        /**
         * 正しくない。
         *
         * @param <T> 値の型
         * @param field 項目の名前
         * @param reason 理由
         */
        record Invalid<T>(String field, FieldErrorReason reason) implements Validation<T> {}
    }

    /**
     * 保存の入力を判定する。
     *
     * @param scope 範囲の入力（無ければ null）
     * @param entries 対象ごとの入力（無ければ null）
     * @return 判定の結果
     */
    public static Validation<SaveCommand> save(TargetInput scope, List<EntryInput> entries) {
        if (scope == null) {
            return new Validation.Invalid<>("scope", FieldErrorReason.REQUIRED);
        }
        if (scope.columnName() != null) {
            return new Validation.Invalid<>("scope.columnName", FieldErrorReason.INVALID_VALUE);
        }
        Validation<PermissionTarget> scopeTarget = target("scope", scope.schemaName(), scope.tableName(), null);
        if (!(scopeTarget instanceof Validation.Valid<PermissionTarget>(PermissionTarget scopeValue))) {
            return invalid(scopeTarget);
        }
        if (entries == null || entries.isEmpty()) {
            return new Validation.Invalid<>("entries", FieldErrorReason.REQUIRED);
        }
        Map<PermissionTarget, PermissionValues> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            String field = "entries[" + i + "]";
            EntryInput entry = entries.get(i);
            if (entry == null) {
                return new Validation.Invalid<>(field, FieldErrorReason.REQUIRED);
            }
            Validation<PermissionTarget> target =
                    target(field, entry.schemaName(), entry.tableName(), entry.columnName());
            if (!(target instanceof Validation.Valid<PermissionTarget>(PermissionTarget targetValue))) {
                return invalid(target);
            }
            Optional<MainPermission> main = MainPermission.parse(entry.main());
            if (entry.main() != null && main.isEmpty()) {
                return new Validation.Invalid<>(field + ".main", FieldErrorReason.INVALID_VALUE);
            }
            if (targetValue.level() == PermissionLevel.COLUMN && (entry.create() != null || entry.delete() != null)) {
                return new Validation.Invalid<>(
                        field + (entry.create() != null ? ".create" : ".delete"), FieldErrorReason.INVALID_VALUE);
            }
            if (!inScope(scopeValue, targetValue) || values.containsKey(targetValue)) {
                return new Validation.Invalid<>(field, FieldErrorReason.INVALID_VALUE);
            }
            values.put(targetValue, new PermissionValues(main.orElse(null), entry.create(), entry.delete()));
        }
        return new Validation.Valid<>(new SaveCommand(scopeValue, values));
    }

    /**
     * 消す操作の入力を判定する（今の DSL との照らし合わせは業務処理が行う）。
     *
     * @param targets 対象の入力（無ければ null）
     * @return 判定の結果
     */
    public static Validation<ClearCommand> clear(List<TargetInput> targets) {
        if (targets == null || targets.isEmpty()) {
            return new Validation.Invalid<>("targets", FieldErrorReason.REQUIRED);
        }
        List<PermissionTarget> values = new ArrayList<>();
        Set<PermissionTarget> seen = new LinkedHashSet<>();
        for (int i = 0; i < targets.size(); i++) {
            String field = "targets[" + i + "]";
            TargetInput input = targets.get(i);
            if (input == null) {
                return new Validation.Invalid<>(field, FieldErrorReason.REQUIRED);
            }
            Validation<PermissionTarget> target =
                    target(field, input.schemaName(), input.tableName(), input.columnName());
            if (!(target instanceof Validation.Valid<PermissionTarget>(PermissionTarget value))) {
                return invalid(target);
            }
            if (!seen.add(value)) {
                return new Validation.Invalid<>(field, FieldErrorReason.INVALID_VALUE);
            }
            values.add(value);
        }
        return new Validation.Valid<>(new ClearCommand(values));
    }

    /** 保存の範囲の中かを返す（スキーマの範囲はそのスキーマだけ、テーブルの範囲はそのテーブルとそのカラム）。 */
    private static boolean inScope(PermissionTarget scope, PermissionTarget target) {
        if (!scope.schemaName().equals(target.schemaName())) {
            return false;
        }
        if (scope.tableName() == null) {
            return target.level() == PermissionLevel.SCHEMA;
        }
        return scope.tableName().equals(target.tableName());
    }

    private static Validation<PermissionTarget> target(
            String field, String schemaName, String tableName, String columnName) {
        Optional<FieldErrorReason> schema = nameProblem(schemaName, true);
        if (schema.isPresent()) {
            return new Validation.Invalid<>(field + ".schemaName", schema.get());
        }
        if (tableName != null) {
            Optional<FieldErrorReason> table = nameProblem(tableName, false);
            if (table.isPresent()) {
                return new Validation.Invalid<>(field + ".tableName", table.get());
            }
        }
        if (columnName != null) {
            Optional<FieldErrorReason> column = nameProblem(columnName, false);
            if (column.isPresent() || tableName == null) {
                return new Validation.Invalid<>(field + ".columnName", column.orElse(FieldErrorReason.INVALID_VALUE));
            }
        }
        return new Validation.Valid<>(new PermissionTarget(schemaName, tableName, columnName));
    }

    private static Optional<FieldErrorReason> nameProblem(String name, boolean required) {
        return PermissionTargetName.problemOf(name).map(reason -> switch (reason) {
            case BLANK -> required ? FieldErrorReason.REQUIRED : FieldErrorReason.INVALID_VALUE;
            case TOO_LONG -> FieldErrorReason.TOO_LONG;
            case CONTROL_CHARACTER -> FieldErrorReason.INVALID_CHARACTER;
        });
    }

    private static <T> Validation<T> invalid(Validation<?> invalid) {
        Validation.Invalid<?> value = (Validation.Invalid<?>) invalid;
        return new Validation.Invalid<>(value.field(), value.reason());
    }
}
