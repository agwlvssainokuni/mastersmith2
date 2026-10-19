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

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;

/**
 * 業務データの問題の種類（BR1.9）。1つの code に1つの状態コードを固定し、code は一度決めたら変えない。403 と 400 は共通の
 * {@code ACCESS_DENIED}・{@code VALIDATION_FAILED} を使い回し、ここには置かない。U2 は書き込みの code を足し、
 * {@code BUSINESS_TARGET_DB_*} を使い回す。
 *
 * <p>説明文に、スキーマ・テーブル・カラムの名前と行の値を載せない。
 */
public final class BusinessDataProblemTypes {

    /** DSL が適用されていない（404。BR1.2）。 */
    public static final ProblemType BUSINESS_DSL_NOT_APPLIED = new ProblemType(
            "BUSINESS_DSL_NOT_APPLIED",
            404,
            new LocalizedText("DSL が適用されていません", "No DSL is applied"),
            new LocalizedText(
                    "業務データの定義（DSL）がまだ適用されていないため、業務データを表示できません。",
                    "Business data cannot be shown because no definition (DSL) has been applied yet."),
            new LocalizedText("管理者に DSL の適用を依頼してください。", "Ask an administrator to apply a DSL."));

    /** 適用中の DSL に無いスキーマかテーブル（404。BR1.3）。 */
    public static final ProblemType BUSINESS_TABLE_NOT_FOUND = new ProblemType(
            "BUSINESS_TABLE_NOT_FOUND",
            404,
            new LocalizedText("テーブルが見つかりません", "Table not found"),
            new LocalizedText("指定したテーブルは、適用中の DSL にありません。", "The specified table is not in the applied DSL."),
            new LocalizedText("メニューからテーブルを選び直してください。", "Choose the table again from the menu."));

    /** 主キーで指した行が無い（404。BR1.7）。 */
    public static final ProblemType BUSINESS_ROW_NOT_FOUND = new ProblemType(
            "BUSINESS_ROW_NOT_FOUND",
            404,
            new LocalizedText("行が見つかりません", "Row not found"),
            new LocalizedText(
                    "指定した行はありません。ほかの人が削除した可能性があります。",
                    "The specified row does not exist. Someone else may have deleted it."),
            new LocalizedText("一覧を読み直してください。", "Reload the list."));

    /** 主キーとビューの決まりで、行の操作が許されない（409。BR1.5）。 */
    public static final ProblemType BUSINESS_ROW_OPERATION_NOT_ALLOWED = new ProblemType(
            "BUSINESS_ROW_OPERATION_NOT_ALLOWED",
            409,
            new LocalizedText("このテーブルではその操作を行えません", "The operation is not allowed for this table"),
            new LocalizedText(
                    "主キーが無い・主キーを見られない・ビューであるなどの理由で、行を1件に決める操作を行えません。",
                    "An operation on a single row is not possible because the table has no primary key, the primary key"
                            + " is not visible, or the table is a view."),
            null);

    /** 詳細で2行以上が当たり、行が1つに決まらない（409。BR6.3）。 */
    public static final ProblemType BUSINESS_ROW_NOT_UNIQUE = new ProblemType(
            "BUSINESS_ROW_NOT_UNIQUE",
            409,
            new LocalizedText("行が1つに決まりません", "The row is not unique"),
            new LocalizedText("指定した主キーに当たる行が2行以上あります。", "More than one row matches the specified primary key."),
            new LocalizedText(
                    "DSL の主キーの設定を管理者に確かめてもらってください。",
                    "Ask an administrator to check the primary key" + " setting of the DSL."));

    /** DSL の定義と対象DB が合わない（409。テーブル・カラムが無い、保存済みの値を DSL の型で読めない。BR1.8、D1: A）。 */
    public static final ProblemType BUSINESS_TARGET_DB_MISMATCH = new ProblemType(
            "BUSINESS_TARGET_DB_MISMATCH",
            409,
            new LocalizedText("DSL と対象DB が合いません", "The DSL does not match the target database"),
            new LocalizedText(
                    "適用中の DSL にあるテーブル・カラムが対象DB に無いか、保存されている値を DSL の型で読めません。",
                    "A table or column in the applied DSL does not exist in the target database, or a stored value"
                            + " cannot be read as the type in the DSL."),
            new LocalizedText(
                    "管理者に DSL と対象DB の照合を依頼してください。",
                    "Ask an administrator to compare the DSL with the target database."));

    /** 対象DB に接続できない・読み取りの接続の設定が無い（503。BR1.8）。 */
    public static final ProblemType BUSINESS_TARGET_DB_UNAVAILABLE = new ProblemType(
            "BUSINESS_TARGET_DB_UNAVAILABLE",
            503,
            new LocalizedText("対象DB に接続できません", "The target database is unavailable"),
            new LocalizedText(
                    "対象DB に接続できないか、対象DB の接続が設定されていません。",
                    "The target database cannot be reached, or its connection is not configured."),
            new LocalizedText(
                    "しばらく待ってから、もう一度試してください。続くときは管理者に連絡してください。",
                    "Wait a while and try again. If the problem continues, contact an administrator."));

    /** 対象DB の問い合わせが時間切れになった（503。BR1.8・BR5.6）。 */
    public static final ProblemType BUSINESS_TARGET_DB_TIMEOUT = new ProblemType(
            "BUSINESS_TARGET_DB_TIMEOUT",
            503,
            new LocalizedText("対象DB の問い合わせが時間切れになりました", "The target database query timed out"),
            new LocalizedText(
                    "対象DB の問い合わせ（件数の問い合わせを含む）が、時間の上限までに終わりませんでした。",
                    "A query to the target database, including the count query, did not finish within the time limit."),
            new LocalizedText("検索の条件を絞ってから、もう一度試してください。", "Narrow the search conditions and try again."));

    /** 画面が使った定義が今の定義と違う（409。U2 の BR1.5）。 */
    public static final ProblemType BUSINESS_DEFINITION_CHANGED = new ProblemType(
            "BUSINESS_DEFINITION_CHANGED",
            409,
            new LocalizedText("テーブルの定義が変わりました", "The table definition has changed"),
            new LocalizedText(
                    "画面を開いた後に、テーブルの定義（DSL または作業ロール）が変わったため、保存しませんでした。",
                    "Nothing was saved because the table definition (DSL or work role) changed after the screen was"
                            + " opened."),
            new LocalizedText("定義を読み直してから、もう一度保存してください。", "Reload the definition and save again."));

    /** 書き込みの接続の設定が無い（409。U2 の BR1.7）。 */
    public static final ProblemType BUSINESS_WRITE_NOT_CONFIGURED = new ProblemType(
            "BUSINESS_WRITE_NOT_CONFIGURED",
            409,
            new LocalizedText("業務データの書き込みが設定されていません", "Writing business data is not configured"),
            new LocalizedText(
                    "対象DB の書き込みの接続が設定されていないため、業務データを作成・更新・削除できません。",
                    "Business data cannot be created, updated or deleted because the write connection to the target"
                            + " database is not configured."),
            new LocalizedText("管理者に書き込みの設定を依頼してください。", "Ask an administrator to configure writing."));

    /** 同じ値の行がある（409。U2 の BR5）。 */
    public static final ProblemType BUSINESS_DUPLICATE_VALUE = new ProblemType(
            "BUSINESS_DUPLICATE_VALUE",
            409,
            new LocalizedText("同じ値の行があります", "A row with the same value exists"),
            new LocalizedText(
                    "重ならないように決められた項目に、ほかの行と同じ値が入っているため、保存しませんでした。",
                    "Nothing was saved because an item that must be unique has the same value as another row."),
            new LocalizedText("値を変えてから、もう一度保存してください。", "Change the value and save again."));

    /** 参照先に無い値（409。U2 の BR6.3）。 */
    public static final ProblemType BUSINESS_REFERENCE_NOT_FOUND = new ProblemType(
            "BUSINESS_REFERENCE_NOT_FOUND",
            409,
            new LocalizedText("参照先に無い値です", "The referenced value does not exist"),
            new LocalizedText(
                    "ほかのテーブルを参照する項目に、参照先に無い値が入っているため、保存しませんでした。",
                    "Nothing was saved because an item that refers to another table has a value that does not exist"
                            + " there."),
            new LocalizedText("参照先にある値を入れてから、もう一度保存してください。", "Enter an existing value and save again."));

    /** 対象DB が保存を拒んだ（409。U2 の BR6.3・BR6.4）。 */
    public static final ProblemType BUSINESS_SAVE_REJECTED = new ProblemType(
            "BUSINESS_SAVE_REJECTED",
            409,
            new LocalizedText("対象DB が保存を受け付けませんでした", "The target database rejected the save"),
            new LocalizedText(
                    "対象DB の決まり（参照されている行・必須の項目・長さ・桁など）に合わないため、保存しませんでした。",
                    "Nothing was saved because the data does not meet a rule of the target database (a referenced"
                            + " row, a required item, a length or digits)."),
            new LocalizedText(
                    "入力を見直してください。直せないときは管理者に連絡してください。",
                    "Check the input. If you cannot fix it, contact an administrator."));

    private BusinessDataProblemTypes() {}

    /**
     * 業務データの問題の種類をすべて返す（U1 の8つと U2 の5つ）。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(
                BUSINESS_DSL_NOT_APPLIED,
                BUSINESS_TABLE_NOT_FOUND,
                BUSINESS_ROW_NOT_FOUND,
                BUSINESS_ROW_OPERATION_NOT_ALLOWED,
                BUSINESS_ROW_NOT_UNIQUE,
                BUSINESS_TARGET_DB_MISMATCH,
                BUSINESS_TARGET_DB_UNAVAILABLE,
                BUSINESS_TARGET_DB_TIMEOUT,
                BUSINESS_DEFINITION_CHANGED,
                BUSINESS_WRITE_NOT_CONFIGURED,
                BUSINESS_DUPLICATE_VALUE,
                BUSINESS_REFERENCE_NOT_FOUND,
                BUSINESS_SAVE_REJECTED);
    }
}
