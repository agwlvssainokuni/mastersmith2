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
package cherry.mastersmith.user.domain;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;

/**
 * UserAccount の問題の種類（BR8.2、NFR8.1、契約のエラーの code の一覧）。1つの code に1つの状態コードを固定する。
 *
 * <p>入力の誤りは既存の {@code VALIDATION_FAILED}（400）、未認証は既存の {@code AUTHENTICATION_REQUIRED}（401）を使うため、ここには
 * 置かない。
 */
public final class UserProblemTypes {

    /** 今のパスワードの誤り（400）。ログインの状態は変わらないため 401 にしない（BR4.2）。 */
    public static final ProblemType PASSWORD_CURRENT_MISMATCH = new ProblemType(
            "PASSWORD_CURRENT_MISMATCH",
            400,
            new LocalizedText("今のパスワードが正しくありません", "Current password is incorrect"),
            new LocalizedText(
                    "入力された今のパスワードが正しくないため、パスワードを変更できませんでした。",
                    "The password could not be changed because the current password you entered is incorrect."),
            new LocalizedText("今のパスワードを確かめて、もう一度変更してください。", "Check your current password and try again."));

    /**
     * 操作・変更の対象の利用者がいない（404）。利用者の管理（useradmin）とグループのメンバーの追加（group）が使う。
     *
     * <p>Intent 260930-user-admin で {@code useradmin.domain} に置いた定義を、Intent 261004-role-menu の U3 で code・状態コード・文言を
     * 変えずにここへ移した（U3 の BR10.2）。ほかの機能が {@code useradmin} に依存せずに同じ code を使えるようにするため。説明文に対象の
     * 利用者のメールアドレス・氏名・ID を載せない。
     */
    public static final ProblemType USER_NOT_FOUND = new ProblemType(
            "USER_NOT_FOUND",
            404,
            new LocalizedText("利用者が見つかりません", "User not found"),
            new LocalizedText("指定した利用者はいません。", "The specified user does not exist."),
            new LocalizedText("利用者の一覧を読み直してください。", "Reload the user list."));

    private UserProblemTypes() {}

    /**
     * UserAccount の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(PASSWORD_CURRENT_MISMATCH, USER_NOT_FOUND);
    }
}
