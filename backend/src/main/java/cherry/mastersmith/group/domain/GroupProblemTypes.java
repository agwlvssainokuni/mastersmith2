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
package cherry.mastersmith.group.domain;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.user.domain.UserProblemTypes;
import java.util.List;
import java.util.Objects;

/**
 * GroupManagement の問題の種類（Intent 261004-role-menu の U3、BR10.1、契約 C6 の業務の理由の拒否の code）。1つの code に1つの状態
 * コードを固定し、code は一度決めたら変えない。
 *
 * <p>入力の誤りは既存の {@code VALIDATION_FAILED}（400）、未認証は既存の {@code AUTHENTICATION_REQUIRED}（401）、管理者でないは既存の
 * {@code ACCESS_DENIED}（403）、利用者がいないは {@link UserProblemTypes#USER_NOT_FOUND}（404。BR10.2）を使うため、ここには置かない。
 * 説明文にグループの名前・ID・利用者の値を載せない。
 */
public final class GroupProblemTypes {

    /** 操作・読み取りの対象のグループがいない（404。BR2.3）。 */
    public static final ProblemType GROUP_NOT_FOUND = new ProblemType(
            "GROUP_NOT_FOUND",
            404,
            new LocalizedText("グループが見つかりません", "Group not found"),
            new LocalizedText("指定したグループはありません。", "The specified group does not exist."),
            new LocalizedText("グループの一覧を読み直してください。", "Reload the group list."));

    /** 名前がほかのグループと重なる（409。大文字と小文字を区別しない。BR1.4）。 */
    public static final ProblemType GROUP_NAME_DUPLICATE = new ProblemType(
            "GROUP_NAME_DUPLICATE",
            409,
            new LocalizedText("同じ名前のグループがあります", "A group with the same name exists"),
            new LocalizedText(
                    "同じ名前（大文字と小文字の違いを除く）のグループがすでにあります。",
                    "A group with the same name, ignoring upper and lower case, already exists."),
            new LocalizedText("別の名前にしてください。", "Choose another name."));

    /** メンバーかロールの割り当てが残るグループの削除（409。応答に残りの数を載せる。BR4.1・BR4.2）。 */
    public static final ProblemType GROUP_IN_USE = new ProblemType(
            "GROUP_IN_USE",
            409,
            new LocalizedText("グループは使用中です", "The group is in use"),
            new LocalizedText(
                    "メンバーかロールの割り当てが残っているため、グループを削除できません。",
                    "The group cannot be deleted because it still has members or assigned roles."),
            new LocalizedText("先にメンバーとロールの割り当てを外してください。", "Remove the members and the assigned roles first."));

    /** 変えるものが無い（409。同じ名前への変更・重ねての追加・メンバーでない人の外し。BR1.5・BR3.3・BR3.4）。 */
    public static final ProblemType GROUP_NO_CHANGE = new ProblemType(
            "GROUP_NO_CHANGE",
            409,
            new LocalizedText("変えるものがありません", "Nothing to change"),
            new LocalizedText("グループはすでにその状態です。", "The group is already in that state."),
            new LocalizedText("グループを読み直してください。", "Reload the group."));

    /** 排他の待ちの上限切れ（409。行の排他と、一意の鍵・主キーの待ち。監査に残さない。BR5.2）。 */
    public static final ProblemType GROUP_BUSY = new ProblemType(
            "GROUP_BUSY",
            409,
            new LocalizedText("ほかの操作と重なりました", "Another operation is in progress"),
            new LocalizedText(
                    "同じグループへのほかの操作が終わるのを待てませんでした。状態は変わっていません。",
                    "The operation could not wait for another operation on the group. Nothing was changed."),
            new LocalizedText("しばらくしてからやり直してください。", "Try again later."));

    private GroupProblemTypes() {}

    /**
     * 業務の拒否の理由に当たる問題の種類を返す（BR10.1。1つの理由に1つの code）。利用者がいないは user の定義を返す（BR10.2）。
     *
     * @param rejection 業務の拒否の理由
     * @return 問題の種類
     */
    public static ProblemType of(GroupRejection rejection) {
        Objects.requireNonNull(rejection, "rejection");
        return switch (rejection) {
            case GROUP_NOT_FOUND -> GROUP_NOT_FOUND;
            case USER_NOT_FOUND -> UserProblemTypes.USER_NOT_FOUND;
            case NAME_DUPLICATE -> GROUP_NAME_DUPLICATE;
            case IN_USE -> GROUP_IN_USE;
            case NO_CHANGE -> GROUP_NO_CHANGE;
        };
    }

    /**
     * GroupManagement の問題の種類をすべて返す（{@code USER_NOT_FOUND} は user の一覧が持つため含めない）。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(GROUP_NOT_FOUND, GROUP_NAME_DUPLICATE, GROUP_IN_USE, GROUP_NO_CHANGE, GROUP_BUSY);
    }
}
