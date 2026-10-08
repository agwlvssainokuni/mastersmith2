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
package cherry.mastersmith.audit.domain;

/**
 * 監査イベントの失敗の理由（FR9.2）。結果が {@link AuditResult#FAILURE} のときだけ記録する。
 *
 * <p>U2 の {@code LoginFailureReason} と U3 の {@code AccessDeniedReason} の値を、監査ログの区分としてまとめたもの。
 * {@link #USER_NOT_FOUND} は、ログインの失敗（入力されたメールアドレスの利用者がいない）とアクセスの拒否（アクセストークンの
 * 利用者が DB にいない）の両方で使う。利用者の管理の操作（Intent 260930-user-admin の U3）でも、対象の利用者がいないときに
 * {@link #USER_NOT_FOUND}、操作した人の確かめ直しで外れていたときに {@link #NOT_ADMIN} を使う。
 */
public enum AuditFailureReason {
    /** 利用者がいない。 */
    USER_NOT_FOUND,
    /** パスワードの誤り。 */
    PASSWORD_MISMATCH,
    /** ロック中。 */
    ACCOUNT_LOCKED,
    /** 利用停止中のログイン（Intent 260930-user-admin の U1、契約 C7）。 */
    ACCOUNT_SUSPENDED,
    /** 管理者でない利用者の要求（403）。 */
    NOT_ADMIN,
    /** アクセストークンが無い要求（401）。 */
    TOKEN_MISSING,
    /** アクセストークンの形式の誤り（401）。 */
    TOKEN_MALFORMED,
    /** アクセストークンの署名・方式の不一致（401）。 */
    TOKEN_INVALID,
    /** パスワードの変更の今のパスワードの誤り（Intent 260925-user-management の U2、契約 C8）。 */
    CURRENT_PASSWORD_MISMATCH,
    /** 招待の期限切れ・置き換え済み（Intent 260925-user-management の U3、契約 C8）。 */
    INVITATION_EXPIRED,
    /** 登録を完了した招待の再使用（U3）。 */
    INVITATION_ALREADY_USED,
    /** 取り消した招待（U3）。 */
    INVITATION_CANCELLED,
    /** 見つからない招待（存在しない・改ざん・形の誤り・送り直しで古くなった・定期の削除で消えた。U3）。 */
    INVITATION_NOT_FOUND,
    /** 登録の完了の時点で同じメールアドレスの利用者がいる（U3、契約 C8 に足した値）。 */
    EMAIL_ALREADY_REGISTERED,
    /** 管理の操作の対象が操作した人自身（Intent 260930-user-admin の U3、契約 C6）。 */
    SELF_OPERATION,
    /** 管理の操作の対象が停止中（Intent 260930-user-admin の U3）。 */
    TARGET_SUSPENDED,
    /** 管理の操作で変えるものが無い（Intent 260930-user-admin の U3）。 */
    NO_CHANGE,
    /** 管理の操作で有効な管理者が 0 人になる（Intent 260930-user-admin の U3）。 */
    LAST_ACTIVE_ADMIN,
    /** 操作の対象のグループがいない（Intent 261004-role-menu の U3、契約 C10）。 */
    GROUP_NOT_FOUND,
    /** グループの名前がほかのグループと重なる（大文字と小文字を区別しない。Intent 261004-role-menu の U3）。 */
    GROUP_NAME_DUPLICATE,
    /** メンバーかロールの割り当てが残るグループの削除（Intent 261004-role-menu の U3）。 */
    GROUP_IN_USE,
    /** 操作の対象のロールがいない（Intent 261004-role-menu の U4、契約 C10）。 */
    ROLE_NOT_FOUND,
    /** ロールの名前がほかのロールと重なる（大文字と小文字を区別しない。Intent 261004-role-menu の U4）。 */
    ROLE_NAME_DUPLICATE,
    /** 割り当てが残るロールの削除（Intent 261004-role-menu の U4）。 */
    ROLE_IN_USE,
    /** 今の DSL に無い対象への権限の設定（Intent 261004-role-menu の U4）。 */
    PERMISSION_TARGET_NOT_IN_DSL,
    /** 適用済みの DSL が無いときの権限の保存（Intent 261004-role-menu の U4）。 */
    DSL_NOT_APPLIED,
    /** 割り当ての外・存在しないロールへの作業ロールの切り替え（Intent 261004-role-menu の U4）。 */
    ROLE_NOT_ASSIGNED
}
