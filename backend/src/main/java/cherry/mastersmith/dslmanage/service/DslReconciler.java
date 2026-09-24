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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.i18n.domain.DisplayLanguage;
import cherry.mastersmith.dsl.domain.DbType;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dslmanage.domain.PreviewView.Warning;
import cherry.mastersmith.dslmanage.domain.PreviewView.WarningKind;
import cherry.mastersmith.targetdb.domain.TargetColumn;
import cherry.mastersmith.targetdb.domain.TargetDbType;
import cherry.mastersmith.targetdb.domain.TargetSchema;
import cherry.mastersmith.targetdb.domain.TargetSchemaResult;
import cherry.mastersmith.targetdb.domain.TargetTable;
import cherry.mastersmith.targetdb.domain.UnavailableReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * プレビューのモデルを対象DB の写し（U1）と照合し、食い違いを警告にする（BR2.4）。DB にも時計にも触れない純粋な関数。
 *
 * <p>無いテーブル（{@code TABLE_MISSING}）・無いカラム（{@code COLUMN_MISSING}）・型の違い（{@code TYPE_MISMATCH}）を DSL の順に
 * 並べる。名前は大文字・小文字を区別して比べる（U1 の BR2.3）。型は、名前を大文字・小文字を区別せずに比べ、長さ・精度・桁は DSL に
 * 書かれているもの（null でないもの）だけを比べる（U3 は DSL の整数に収まらない長さを書かないため、そのときの対象DB の長さは比べない）。
 * U1 が設定なし・接続できないを返したときは、その警告を1件だけにする。どれも失敗にしない。警告には DSL の中の名前と型だけを入れ、
 * 接続先と内部の例外の文言を入れない。
 */
public final class DslReconciler {

    private static final LocalizedText TABLE_MISSING =
            new LocalizedText("対象DB にテーブル「{0}」がありません。", "The table \"{0}\" does not exist in the target database.");

    private static final LocalizedText COLUMN_MISSING = new LocalizedText(
            "対象DB のテーブル「{0}」にカラム「{1}」がありません。",
            "The column \"{1}\" does not exist in the table \"{0}\" of the target database.");

    private static final LocalizedText TYPE_MISMATCH = new LocalizedText(
            "カラム「{0}.{1}」の型が対象DB と違います（DSL: {2}、対象DB: {3}）。",
            "The type of the column \"{0}.{1}\" differs from the target database (DSL: {2}, target database: {3}).");

    private static final LocalizedText UNCONFIGURED = new LocalizedText(
            "対象DB の接続先が設定されていないため、照合できませんでした。",
            "The DSL could not be compared because the target database is not configured.");

    private static final LocalizedText TIMEOUT = new LocalizedText(
            "対象DB が時間内に応答しなかったため、照合できませんでした。",
            "The DSL could not be compared because the target database did not respond in time.");

    private static final LocalizedText CONNECTION_FAILED = new LocalizedText(
            "対象DB に接続できなかったため、照合できませんでした。",
            "The DSL could not be compared because the target database could not be connected.");

    private DslReconciler() {}

    /**
     * 照合の警告を求める。
     *
     * @param model プレビューのモデル
     * @param target 対象DB の読み取りの結果（U1 の {@code readSchema(COMPARE)}）
     * @param language 文言の表示言語
     * @return 警告（食い違いが無ければ空）
     */
    public static List<Warning> reconcile(DslModel model, TargetSchemaResult target, DisplayLanguage language) {
        return switch (target) {
            case TargetSchemaResult.Success success -> compare(model, success.schema(), language);
            case TargetSchemaResult.Unconfigured unconfigured ->
                List.of(new Warning(WarningKind.TARGET_UNCONFIGURED, null, UNCONFIGURED.in(language)));
            case TargetSchemaResult.Unavailable unavailable ->
                List.of(new Warning(
                        WarningKind.TARGET_UNAVAILABLE,
                        null,
                        (unavailable.reason() == UnavailableReason.TIMEOUT ? TIMEOUT : CONNECTION_FAILED)
                                .in(language)));
        };
    }

    private static List<Warning> compare(DslModel model, TargetSchema schema, DisplayLanguage language) {
        List<Warning> warnings = new ArrayList<>();
        for (DslTable table : model.tables().values()) {
            String tablePath = "tables." + table.name();
            Optional<TargetTable> found = schema.table(table.name());
            if (found.isEmpty()) {
                warnings.add(new Warning(
                        WarningKind.TABLE_MISSING, tablePath, format(TABLE_MISSING.in(language), table.name())));
                continue;
            }
            Map<String, TargetColumn> columns =
                    found.get().columns().stream().collect(Collectors.toMap(TargetColumn::name, Function.identity()));
            for (DslColumn column : table.columns().values()) {
                String columnPath = tablePath + ".columns." + column.name();
                TargetColumn targetColumn = columns.get(column.name());
                if (targetColumn == null) {
                    warnings.add(new Warning(
                            WarningKind.COLUMN_MISSING,
                            columnPath,
                            format(COLUMN_MISSING.in(language), table.name(), column.name())));
                } else if (!typeMatches(column.dbType(), targetColumn.dbType())) {
                    warnings.add(new Warning(
                            WarningKind.TYPE_MISMATCH,
                            columnPath,
                            format(
                                    TYPE_MISMATCH.in(language),
                                    table.name(),
                                    column.name(),
                                    describe(column.dbType()),
                                    describe(targetColumn.dbType()))));
                }
            }
        }
        return warnings;
    }

    /**
     * DSL の型が対象DB の型と合うかを返す。
     *
     * @param dsl DSL の型
     * @param target 対象DB の型
     * @return 合えば true
     */
    static boolean typeMatches(DbType dsl, TargetDbType target) {
        if (!dsl.name().toLowerCase(Locale.ROOT).equals(target.typeName().toLowerCase(Locale.ROOT))) {
            return false;
        }
        return matchesIfWritten(dsl.length(), targetLength(target))
                && matchesIfWritten(dsl.precision(), target.precision())
                && matchesIfWritten(dsl.scale(), target.scale());
    }

    private static boolean matchesIfWritten(Integer written, Integer actual) {
        return written == null || written.equals(actual);
    }

    private static Integer targetLength(TargetDbType target) {
        Long length = target.length();
        return length == null || length > Integer.MAX_VALUE ? null : length.intValue();
    }

    private static String describe(DbType type) {
        return describe(type.name(), type.length(), type.precision(), type.scale());
    }

    private static String describe(TargetDbType type) {
        return describe(type.typeName(), targetLength(type), type.precision(), type.scale());
    }

    private static String describe(String name, Integer length, Integer precision, Integer scale) {
        if (length != null) {
            return name + "(" + length + ")";
        }
        if (precision != null) {
            return name + "(" + precision + (scale == null ? "" : "," + scale) + ")";
        }
        return name;
    }

    /**
     * 文言の {@code {0}}・{@code {1}} … を値で置き換える。
     *
     * @param template 文言
     * @param args 埋める値
     * @return 置き換えた文言
     */
    static String format(String template, String... args) {
        String result = Objects.requireNonNull(template);
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }
}
