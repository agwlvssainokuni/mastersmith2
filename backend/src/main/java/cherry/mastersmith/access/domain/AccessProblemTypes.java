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
package cherry.mastersmith.access.domain;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;

/**
 * U3 の問題の種類（BR6.1。{@code REQUEST_REJECTED} は NFR Design の {@code security-design.md} 2章で加えたもので、計画の
 * D3 で依頼者の承認を得ている）。
 *
 * <p>説明には、判定の詳細・要求のパス・内部の情報を載せない（NFR3.6）。
 */
public final class AccessProblemTypes {

    /** 管理者のみの API への権限不足のアクセス（403）。 */
    public static final ProblemType ACCESS_DENIED = new ProblemType(
            "ACCESS_DENIED",
            403,
            new LocalizedText("この操作を行う権限がありません", "Access denied"),
            new LocalizedText(
                    "この操作には管理者の権限が必要です。ログイン中の利用者には権限がありません。",
                    "This operation requires administrator privileges, which the logged-in user does not have."),
            new LocalizedText("権限が必要な場合は、管理者に連絡してください。", "If you need the privileges, contact an administrator."));

    /** 正規化されていないパスの要求の拒否（400）。 */
    public static final ProblemType REQUEST_REJECTED = new ProblemType(
            "REQUEST_REJECTED",
            400,
            new LocalizedText("要求を受け付けられません", "Request rejected"),
            new LocalizedText(
                    "要求の URL に、安全のために受け付けられない書き方が含まれています。",
                    "The URL of the request contains a form that is not accepted for security reasons."),
            new LocalizedText("このアプリの画面から操作してください。", "Use this application's screens to perform the operation."));

    private AccessProblemTypes() {}

    /**
     * U3 の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(ACCESS_DENIED, REQUEST_REJECTED);
    }
}
