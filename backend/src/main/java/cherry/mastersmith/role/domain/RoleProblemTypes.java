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
package cherry.mastersmith.role.domain;

import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import java.util.List;
import java.util.Objects;

/**
 * RoleManagement の問題の種類（Intent 261004-role-menu の U4、BR13.1、計画の 4.2）。1つの code に1つの状態コードを固定し、code は一度
 * 決めたら変えない。B4 の7つ。{@code ROLE_NOT_ASSIGNED}（B5）・{@code ROLE_TRANSFER_INVALID}・{@code ROLE_TRANSFER_STALE}（B6）は
 * その Bolt で足す。
 *
 * <p>入力の誤りは既存の {@code VALIDATION_FAILED}（400）、未認証は既存の {@code AUTHENTICATION_REQUIRED}（401）、管理者でないは既存の
 * {@code ACCESS_DENIED}（403）を使うため、ここには置かない（BR13.2）。{@code DSL_NOT_APPLIED} は今のアプリに同じ code が無いため
 * role に定義し、U5 navigation が使うときはこの定義を使い回す（計画の D-36）。説明文にロールの名前・ID・対象の名前を載せない。
 */
public final class RoleProblemTypes {

    /** 操作・読み取りの対象のロールがいない（404。0 以下の ID も同じ。計画の D-13）。 */
    public static final ProblemType ROLE_NOT_FOUND = new ProblemType(
            "ROLE_NOT_FOUND",
            404,
            new LocalizedText("ロールが見つかりません", "Role not found"),
            new LocalizedText("指定したロールはありません。", "The specified role does not exist."),
            new LocalizedText("ロールの一覧を読み直してください。", "Reload the role list."));

    /** 名前がほかのロールと重なる（409。大文字と小文字を区別しない。BR1.4）。 */
    public static final ProblemType ROLE_NAME_DUPLICATE = new ProblemType(
            "ROLE_NAME_DUPLICATE",
            409,
            new LocalizedText("同じ名前のロールがあります", "A role with the same name exists"),
            new LocalizedText(
                    "同じ名前（大文字と小文字の違いを除く）のロールがすでにあります。",
                    "A role with the same name, ignoring upper and lower case, already exists."),
            new LocalizedText("別の名前にしてください。", "Choose another name."));

    /** 割り当てが残るロールの削除（409。応答に残りの数を載せる。BR3.2）。 */
    public static final ProblemType ROLE_IN_USE = new ProblemType(
            "ROLE_IN_USE",
            409,
            new LocalizedText("ロールは使用中です", "The role is in use"),
            new LocalizedText(
                    "利用者かグループへの割り当てが残っているため、ロールを削除できません。",
                    "The role cannot be deleted because it is still assigned to users or groups."),
            new LocalizedText("先に割り当てを外してください。", "Remove the assignments first."));

    /** 変えるものが無い（409。BR1.5・BR4.7・BR4.8）。 */
    public static final ProblemType ROLE_NO_CHANGE = new ProblemType(
            "ROLE_NO_CHANGE",
            409,
            new LocalizedText("変えるものがありません", "Nothing to change"),
            new LocalizedText("ロールはすでにその状態です。", "The role is already in that state."),
            new LocalizedText("ロールを読み直してください。", "Reload the role."));

    /** 排他の待ちの上限切れ（409。ロールの行と、一意の鍵の待ち。監査に残さない。BR8.3・BR8.4）。 */
    public static final ProblemType ROLE_BUSY = new ProblemType(
            "ROLE_BUSY",
            409,
            new LocalizedText("ほかの操作と重なりました", "Another operation is in progress"),
            new LocalizedText(
                    "同じロールへのほかの操作が終わるのを待てませんでした。状態は変わっていません。",
                    "The operation could not wait for another operation on the role. Nothing was changed."),
            new LocalizedText("しばらくしてからやり直してください。", "Try again later."));

    /** 今の DSL に無い対象への値の設定（409。BR4.6）。 */
    public static final ProblemType PERMISSION_TARGET_NOT_IN_DSL = new ProblemType(
            "PERMISSION_TARGET_NOT_IN_DSL",
            409,
            new LocalizedText("今の DSL に無い対象です", "The target is not in the current DSL"),
            new LocalizedText(
                    "今の DSL に無い対象には権限を設定できません。設定は変わっていません。",
                    "Permissions cannot be set for a target that is not in the current DSL. Nothing was changed."),
            new LocalizedText(
                    "権限の設定を読み直し、今の DSL に無い設定は消してください。",
                    "Reload the permission settings and clear the settings that are not in the current DSL."));

    /** 適用済みの DSL が無い（409。BR4.5）。 */
    public static final ProblemType DSL_NOT_APPLIED = new ProblemType(
            "DSL_NOT_APPLIED",
            409,
            new LocalizedText("DSL が適用されていません", "No DSL is applied"),
            new LocalizedText(
                    "適用済みの DSL が無いため、権限を設定する対象がありません。",
                    "There is no target for permissions because no DSL is applied."),
            new LocalizedText("DSL の管理の画面で DSL を適用してください。", "Apply a DSL on the DSL management screen."));

    private RoleProblemTypes() {}

    /**
     * 業務の拒否の理由に当たる問題の種類を返す（BR13.1。1つの理由に1つの code）。
     *
     * @param rejection 業務の拒否の理由
     * @return 問題の種類
     */
    public static ProblemType of(RoleRejection rejection) {
        Objects.requireNonNull(rejection, "rejection");
        return switch (rejection) {
            case ROLE_NOT_FOUND -> ROLE_NOT_FOUND;
            case NAME_DUPLICATE -> ROLE_NAME_DUPLICATE;
            case IN_USE -> ROLE_IN_USE;
            case NO_CHANGE -> ROLE_NO_CHANGE;
            case TARGET_NOT_IN_DSL -> PERMISSION_TARGET_NOT_IN_DSL;
            case DSL_NOT_APPLIED -> DSL_NOT_APPLIED;
        };
    }

    /**
     * RoleManagement の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(
                ROLE_NOT_FOUND,
                ROLE_NAME_DUPLICATE,
                ROLE_IN_USE,
                ROLE_NO_CHANGE,
                ROLE_BUSY,
                PERMISSION_TARGET_NOT_IN_DSL,
                DSL_NOT_APPLIED);
    }
}
