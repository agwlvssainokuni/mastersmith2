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
package cherry.mastersmith.useradmin.domain;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;

/**
 * UserAdministration の問題の種類（Intent 260930-user-admin の U3、BR2.3・NFR8.1、契約のエラーの code の一覧）。1つの code に1つの
 * 状態コードを固定する。
 *
 * <p>入力の誤りは既存の {@code VALIDATION_FAILED}（400）、未認証は既存の {@code AUTHENTICATION_REQUIRED}（401）、管理者でないは既存の
 * {@code ACCESS_DENIED}（403）を使うため、ここには置かない。説明文に対象の利用者のメールアドレス・氏名・ID を載せない（BR7.5）。
 */
public final class UserAdminProblemTypes {

    /** 操作・変更の対象の利用者がいない（404。BR2.3・BR5.2）。 */
    public static final ProblemType USER_NOT_FOUND = new ProblemType(
            "USER_NOT_FOUND",
            404,
            new LocalizedText("利用者が見つかりません", "User not found"),
            new LocalizedText("指定した利用者はいません。", "The specified user does not exist."),
            new LocalizedText("利用者の一覧を読み直してください。", "Reload the user list."));

    private UserAdminProblemTypes() {}

    /**
     * UserAdministration の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(USER_NOT_FOUND);
    }
}
