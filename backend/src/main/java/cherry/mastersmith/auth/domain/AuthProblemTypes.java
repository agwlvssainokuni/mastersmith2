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
package cherry.mastersmith.auth.domain;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;

/**
 * U2 の問題の種類（BR9.1。{@code ORIGIN_NOT_ALLOWED} は NFR Design の security-design 8章で加えたもの）。
 *
 * <p>説明には失敗の理由（存在しないメールアドレス・パスワード誤り・ロック中など）を載せない（BR2.4）。
 */
public final class AuthProblemTypes {

    /** ログインの失敗（理由によらず同じ）（401）。 */
    public static final ProblemType AUTHENTICATION_FAILED = new ProblemType(
            "AUTHENTICATION_FAILED",
            401,
            new LocalizedText("ログインできませんでした", "Authentication failed"),
            new LocalizedText(
                    "メールアドレスまたはパスワードが正しくないため、ログインできませんでした。",
                    "The login failed because the email address or the password is not correct."),
            new LocalizedText(
                    "メールアドレスとパスワードを確かめて、もう一度ログインしてください。",
                    "Check the email address and the password, and log in again."));

    /** ログインが必要（アクセストークンが無い・無効）（401）。 */
    public static final ProblemType AUTHENTICATION_REQUIRED = new ProblemType(
            "AUTHENTICATION_REQUIRED",
            401,
            new LocalizedText("ログインが必要です", "Authentication required"),
            new LocalizedText(
                    "この操作にはログインが必要です。ログインしていないか、ログインの有効期限が切れています。",
                    "This operation requires login. You are not logged in, or your login has expired."),
            new LocalizedText("もう一度ログインしてください。", "Log in again."));

    /** トークンの更新の失敗（401）。 */
    public static final ProblemType REFRESH_FAILED = new ProblemType(
            "REFRESH_FAILED",
            401,
            new LocalizedText("ログインの状態を更新できませんでした", "Session refresh failed"),
            new LocalizedText(
                    "ログインの状態が無効か期限切れのため、更新できませんでした。",
                    "The session could not be refreshed because it is invalid or has expired."),
            new LocalizedText("もう一度ログインしてください。", "Log in again."));

    /** 要求の送り元が自分の配信元と一致しない（403）。 */
    public static final ProblemType ORIGIN_NOT_ALLOWED = new ProblemType(
            "ORIGIN_NOT_ALLOWED",
            403,
            new LocalizedText("許可されていない送り元です", "Origin not allowed"),
            new LocalizedText(
                    "要求の送り元（Origin）がこのアプリの配信元と一致しないため、受け付けませんでした。",
                    "The request was rejected because its origin does not match this application's origin."),
            new LocalizedText("このアプリの画面から操作してください。", "Use this application's screens to perform the operation."));

    private AuthProblemTypes() {}

    /**
     * U2 の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(AUTHENTICATION_FAILED, AUTHENTICATION_REQUIRED, REFRESH_FAILED, ORIGIN_NOT_ALLOWED);
    }
}
