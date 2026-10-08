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
import cherry.mastersmith.user.domain.UserProblemTypes;
import java.util.List;
import java.util.Objects;

/**
 * UserAdministration の問題の種類（Intent 260930-user-admin の U3、BR2.3・NFR8.1、契約のエラーの code の一覧）。1つの code に1つの
 * 状態コードを固定する。B4（Intent 260930-user-admin の U3 後半）で5つの操作の 409 を足した。
 *
 * <p>入力の誤りは既存の {@code VALIDATION_FAILED}（400）、未認証は既存の {@code AUTHENTICATION_REQUIRED}（401）、管理者でないは既存の
 * {@code ACCESS_DENIED}（403）を使うため、ここには置かない。説明文に対象の利用者のメールアドレス・氏名・ID を載せない（BR7.5）。
 *
 * <p>対象の利用者がいない（{@code USER_NOT_FOUND}、404）は、Intent 261004-role-menu の U3 で {@link UserProblemTypes} へ移した（U3 の
 * BR10.2。code・状態コード・文言は変えていない）。{@link #of(RejectionReason)} はその定義を返し、{@link #all()} には含めない（起動時の
 * 一覧は {@code UserProblemTypeCatalog} が持つ。code の二重の定義は起動時の検査で起動を止めるため）。
 */
public final class UserAdminProblemTypes {

    /** 自分自身への操作（409。BR2.2・BR2.3）。 */
    public static final ProblemType SELF_OPERATION = new ProblemType(
            "USER_ADMIN_SELF_OPERATION",
            409,
            new LocalizedText("自分自身には行えない操作です", "This operation cannot be applied to yourself"),
            new LocalizedText(
                    "自分自身の管理者の印・利用の停止は変えられません。", "You cannot change your own administrator flag or suspension."),
            new LocalizedText("ほかの管理者に依頼してください。", "Ask another administrator."));

    /** 対象の利用者が停止中で、管理者の印を変えられない（409。BR2.2・BR2.3）。 */
    public static final ProblemType TARGET_SUSPENDED = new ProblemType(
            "USER_ADMIN_TARGET_SUSPENDED",
            409,
            new LocalizedText("対象の利用者は停止中です", "The user is suspended"),
            new LocalizedText(
                    "停止中の利用者の管理者の印は変えられません。", "The administrator flag of a suspended user cannot be changed."),
            new LocalizedText("先に停止を解いてください。", "Resume the user first."));

    /** 変えるものが無い（409。BR2.2・BR2.3・BR4.5）。 */
    public static final ProblemType NO_CHANGE = new ProblemType(
            "USER_ADMIN_NO_CHANGE",
            409,
            new LocalizedText("変えるものがありません", "Nothing to change"),
            new LocalizedText("利用者はすでにその状態です。", "The user is already in that state."),
            new LocalizedText("利用者の一覧を読み直してください。", "Reload the user list."));

    /** 有効な管理者が 0 人になる（409。BR3.2・BR2.3）。 */
    public static final ProblemType LAST_ADMIN = new ProblemType(
            "USER_ADMIN_LAST_ADMIN",
            409,
            new LocalizedText("最後の有効な管理者です", "This is the last active administrator"),
            new LocalizedText(
                    "有効な管理者が1人もいなくなる操作は行えません。", "An operation that leaves no active administrator is not allowed."),
            new LocalizedText("先にほかの利用者を管理者にしてください。", "Make another user an administrator first."));

    /** 行の排他の待ちの上限切れ（409。BR3.5）。 */
    public static final ProblemType BUSY = new ProblemType(
            "USER_ADMIN_BUSY",
            409,
            new LocalizedText("ほかの操作と重なりました", "Another operation is in progress"),
            new LocalizedText(
                    "同じ利用者へのほかの操作が終わるのを待てませんでした。状態は変わっていません。",
                    "The operation could not wait for another operation on the user. Nothing was changed."),
            new LocalizedText("しばらくしてからやり直してください。", "Try again later."));

    private UserAdminProblemTypes() {}

    /**
     * 業務の拒否の理由に当たる問題の種類を返す（BR2.3。1つの理由に1つの code）。
     *
     * @param reason 業務の拒否の理由
     * @return 問題の種類
     */
    public static ProblemType of(RejectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        return switch (reason) {
            case USER_NOT_FOUND -> UserProblemTypes.USER_NOT_FOUND;
            case SELF_OPERATION -> SELF_OPERATION;
            case TARGET_SUSPENDED -> TARGET_SUSPENDED;
            case NO_CHANGE -> NO_CHANGE;
            case LAST_ACTIVE_ADMIN -> LAST_ADMIN;
        };
    }

    /**
     * UserAdministration の問題の種類をすべて返す。
     *
     * @return 問題の種類の一覧
     */
    public static List<ProblemType> all() {
        return List.of(SELF_OPERATION, TARGET_SUSPENDED, NO_CHANGE, LAST_ADMIN, BUSY);
    }
}
