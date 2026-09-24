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
package cherry.mastersmith.dslmanage.domain;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;

/**
 * U4 の問題の種類（契約 C6 の code の一覧、BR8.2、NFR5.5）。1つの code の状態コードは1つだけ。
 *
 * <p>説明には、接続先・内部の例外の文言・要求の内容を載せない（NFR5.4）。
 */
public final class DslProblemTypes {

    /** 検証を通らない DSL（422）。誤りの一覧は追加の項目 {@code errors}・{@code total} に載せる。 */
    public static final ProblemType DSL_INVALID = new ProblemType(
            "DSL_INVALID",
            422,
            new LocalizedText("DSL が検証を通りません", "Invalid DSL"),
            new LocalizedText(
                    "DSL の書式・版・内容のどれかが決まりに合わないため、受け付けませんでした。誤りの一覧を確かめてください。",
                    "The DSL was not accepted because its format, version or content does not meet the rules."
                            + " Check the list of errors."),
            new LocalizedText("誤りを直して、もう一度投入してください。", "Fix the errors and submit the DSL again."));

    /** 本文が投入の上限（10MB）を超えた（413）。 */
    public static final ProblemType DSL_TOO_LARGE = new ProblemType(
            "DSL_TOO_LARGE",
            413,
            new LocalizedText("DSL が大きすぎます", "DSL too large"),
            new LocalizedText("投入した DSL の本文が大きさの上限を超えています。", "The submitted DSL exceeds the size limit."),
            new LocalizedText("DSL を小さくして、もう一度投入してください。", "Reduce the size of the DSL and submit it again."));

    /** プレビューが無い（404）。 */
    public static final ProblemType DSL_PREVIEW_NOT_FOUND = new ProblemType(
            "DSL_PREVIEW_NOT_FOUND",
            404,
            new LocalizedText("プレビューがありません", "No preview"),
            new LocalizedText("プレビュー中の DSL がありません。", "There is no DSL in preview."),
            new LocalizedText("スキーマを読み込むか、DSL を投入してください。", "Load the schema or submit a DSL."));

    /** 適用で指定したプレビューが、今のプレビューと違う・無くなった（409）。 */
    public static final ProblemType DSL_PREVIEW_CHANGED = new ProblemType(
            "DSL_PREVIEW_CHANGED",
            409,
            new LocalizedText("プレビューが変わりました", "Preview changed"),
            new LocalizedText(
                    "適用しようとしたプレビューは、ほかの操作で置き換えられたか、破棄されました。適用中の DSL は変わっていません。",
                    "The preview you tried to apply has been replaced or discarded by another operation."
                            + " The applied DSL has not changed."),
            new LocalizedText("今の状態を表示し直して、プレビューを確かめてください。", "Reload the current state and check the preview."));

    /** 適用中の DSL が無い（404）。 */
    public static final ProblemType DSL_APPLIED_NOT_FOUND = new ProblemType(
            "DSL_APPLIED_NOT_FOUND",
            404,
            new LocalizedText("適用中の DSL がありません", "No applied DSL"),
            new LocalizedText("適用中の DSL がまだありません。", "No DSL has been applied yet."),
            null);

    /** 履歴に指定の版が無い（404）。 */
    public static final ProblemType DSL_REVISION_NOT_FOUND = new ProblemType(
            "DSL_REVISION_NOT_FOUND",
            404,
            new LocalizedText("履歴の版がありません", "Revision not found"),
            new LocalizedText(
                    "指定した版は履歴にありません（件数の上限で消えた場合を含みます）。",
                    "The specified revision is not in the history (it may have been removed by the history limit)."),
            new LocalizedText("履歴を表示し直してください。", "Reload the history."));

    /** 対象DB の設定が無い（503）。 */
    public static final ProblemType TARGET_DB_UNCONFIGURED = new ProblemType(
            "TARGET_DB_UNCONFIGURED",
            503,
            new LocalizedText("対象DB が設定されていません", "Target database not configured"),
            new LocalizedText(
                    "対象DB の接続先が設定されていないため、スキーマを読み込めません。",
                    "The schema cannot be loaded because the target database is not configured."),
            new LocalizedText(
                    "運用者に対象DB の設定を確かめてもらってください。", "Ask the operator to check the settings of the target database."));

    /** 対象DB に接続できない・応答しない（503）。 */
    public static final ProblemType TARGET_DB_UNAVAILABLE = new ProblemType(
            "TARGET_DB_UNAVAILABLE",
            503,
            new LocalizedText("対象DB に接続できません", "Target database unavailable"),
            new LocalizedText(
                    "対象DB に接続できないか、時間内に応答が無いため、スキーマを読み込めません。",
                    "The schema cannot be loaded because the target database cannot be connected or did not respond"
                            + " in time."),
            new LocalizedText(
                    "しばらくしてからやり直すか、運用者に対象DB の状態を確かめてもらってください。",
                    "Try again later, or ask the operator to check the state of the target database."));

    /** 重い処理がほかに進んでいる（503。NFR1.13）。 */
    public static final ProblemType DSL_BUSY = new ProblemType(
            "DSL_BUSY",
            503,
            new LocalizedText("ほかの処理中です", "Busy"),
            new LocalizedText(
                    "DSL のほかの処理（読み込み・投入・履歴からの戻し・プレビューの表示）が進んでいるため、受け付けませんでした。" + "状態は変わっていません。",
                    "The request was not accepted because another DSL operation (loading, submitting, restoring or"
                            + " showing the preview) is in progress. Nothing has changed."),
            new LocalizedText("少し待ってからやり直してください。", "Wait a moment and try again."));

    private DslProblemTypes() {}

    /**
     * U4 の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(
                DSL_INVALID,
                DSL_TOO_LARGE,
                DSL_PREVIEW_NOT_FOUND,
                DSL_PREVIEW_CHANGED,
                DSL_APPLIED_NOT_FOUND,
                DSL_REVISION_NOT_FOUND,
                TARGET_DB_UNCONFIGURED,
                TARGET_DB_UNAVAILABLE,
                DSL_BUSY);
    }
}
