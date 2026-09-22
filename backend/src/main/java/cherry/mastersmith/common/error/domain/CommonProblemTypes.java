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
package cherry.mastersmith.common.error.domain;

import java.util.List;

/** U1 が定義する共通の問題の種類（BR5.6〜BR5.8、BR5.14、NFR3.12。フレームワークの標準の 4xx は計画の P1 の決定）。 */
public final class CommonProblemTypes {

    /** 入力の検証の失敗（400）。 */
    public static final ProblemType VALIDATION_FAILED = new ProblemType(
            "VALIDATION_FAILED",
            400,
            new LocalizedText("入力の検証に失敗しました", "Validation failed"),
            new LocalizedText(
                    "要求の入力値が決まりに合っていません（必須の値が無い、形式や範囲が正しくないなど）。",
                    "The request contains input values that do not meet the requirements"
                            + " (missing required values, wrong format or range, and so on)."),
            new LocalizedText("入力値を見直して、もう一度送ってください。", "Review the input values and send the request again."));

    /** 要求の本文を読み取れない（400）。 */
    public static final ProblemType MALFORMED_REQUEST = new ProblemType(
            "MALFORMED_REQUEST",
            400,
            new LocalizedText("要求の形式が正しくありません", "Malformed request"),
            new LocalizedText(
                    "要求の本文を読み取れませんでした（JSON の形式が正しくないなど）。",
                    "The request body could not be read (for example, the JSON is malformed)."),
            new LocalizedText(
                    "要求の本文の形式を見直して、もう一度送ってください。", "Check the format of the request body and send the request again."));

    /** 存在しない API・情報（404）。 */
    public static final ProblemType NOT_FOUND = new ProblemType(
            "NOT_FOUND",
            404,
            new LocalizedText("見つかりません", "Not found"),
            new LocalizedText("指定された API または情報が見つかりません。", "The requested API or resource could not be found."),
            new LocalizedText("URL を確かめてください。", "Check the URL."));

    /** 許されない HTTP メソッド（405）。 */
    public static final ProblemType METHOD_NOT_ALLOWED = new ProblemType(
            "METHOD_NOT_ALLOWED",
            405,
            new LocalizedText("許可されていない操作です", "Method not allowed"),
            new LocalizedText(
                    "この URL では、要求の HTTP メソッドを使えません。", "The HTTP method of the request is not supported for this URL."),
            new LocalizedText(
                    "使えるメソッドを応答の Allow ヘッダーで確かめてください。",
                    "Check the Allow header of the response for the supported methods."));

    /** 求められた応答の形式で返せない（406）。 */
    public static final ProblemType NOT_ACCEPTABLE = new ProblemType(
            "NOT_ACCEPTABLE",
            406,
            new LocalizedText("応答の形式に対応していません", "Not acceptable"),
            new LocalizedText(
                    "要求された応答の形式（Accept ヘッダー）で応答を返せません。",
                    "The response cannot be returned in the format requested by the Accept header."),
            new LocalizedText("Accept ヘッダーを見直してください。", "Review the Accept header of the request."));

    /** 要求の本文が上限を超えた（413）。 */
    public static final ProblemType PAYLOAD_TOO_LARGE = new ProblemType(
            "PAYLOAD_TOO_LARGE",
            413,
            new LocalizedText("要求が大きすぎます", "Payload too large"),
            new LocalizedText("要求の本文が大きさの上限を超えています。", "The request body exceeds the size limit."),
            new LocalizedText("本文を小さくして、もう一度送ってください。", "Reduce the size of the request body and try again."));

    /** 対応していない本文の形式（415）。 */
    public static final ProblemType UNSUPPORTED_MEDIA_TYPE = new ProblemType(
            "UNSUPPORTED_MEDIA_TYPE",
            415,
            new LocalizedText("対応していない形式です", "Unsupported media type"),
            new LocalizedText(
                    "要求の本文の形式（Content-Type）に対応していません。",
                    "The format of the request body (Content-Type) is not supported."),
            new LocalizedText("Content-Type を見直してください。", "Review the Content-Type of the request."));

    /** 想定外のエラー（500）。 */
    public static final ProblemType INTERNAL_ERROR = new ProblemType(
            "INTERNAL_ERROR",
            500,
            new LocalizedText("サーバーでエラーが起きました", "Internal server error"),
            new LocalizedText("サーバーで想定していないエラーが起きました。", "An unexpected error occurred on the server."),
            new LocalizedText(
                    "しばらくしてから、もう一度お試しください。続く場合は、応答の traceId を添えて管理者に連絡してください。",
                    "Please try again later. If the problem persists,"
                            + " contact the administrator with the traceId in the response."));

    private CommonProblemTypes() {}

    /**
     * U1 が定義する問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(
                VALIDATION_FAILED,
                MALFORMED_REQUEST,
                NOT_FOUND,
                METHOD_NOT_ALLOWED,
                NOT_ACCEPTABLE,
                PAYLOAD_TOO_LARGE,
                UNSUPPORTED_MEDIA_TYPE,
                INTERNAL_ERROR);
    }
}
