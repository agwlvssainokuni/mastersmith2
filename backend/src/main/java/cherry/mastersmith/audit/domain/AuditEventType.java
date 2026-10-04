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
 * 監査イベントの種類（BR1.2、FR9.2。DSL の操作は Intent 260923-dsl-schema-loader の U4、契約 C7。パスワードの変更は Intent
 * 260925-user-management の U2、招待と登録は同じ Intent の U3、契約 C8。利用者の管理の操作は Intent 260930-user-admin の U3、
 * 契約 C6。初期管理者の作成と救済は Intent 261004-safety-carryover の FR1.5）。
 */
public enum AuditEventType {
    /** ログインの成功。 */
    LOGIN_SUCCEEDED,
    /** ログインの失敗。 */
    LOGIN_FAILED,
    /** ログアウト。 */
    LOGGED_OUT,
    /** 管理者のみの API へのアクセスの拒否。 */
    ACCESS_DENIED,
    /** スキーマの読み込みで既定の DSL をプレビューに置いた。 */
    DSL_GENERATED,
    /** DSL を投入した（アップロード・貼り付け・履歴からの戻し）。 */
    DSL_SUBMITTED,
    /** DSL の投入を受け付けなかった。 */
    DSL_SUBMISSION_REJECTED,
    /** DSL を適用した。 */
    DSL_APPLIED,
    /** DSL のプレビューを破棄した。 */
    DSL_PREVIEW_DISCARDED,
    /** パスワードの変更（成功と今のパスワードの誤り。結果は出来事が持つ）。 */
    PASSWORD_CHANGED,
    /** 招待した（Intent 260925-user-management の U3、契約 C8）。 */
    INVITATION_ISSUED,
    /** 招待を送り直した（U3）。 */
    INVITATION_RESENT,
    /** 招待を取り消した（U3）。 */
    INVITATION_CANCELLED,
    /** 招待から登録を完了した（U3）。 */
    REGISTRATION_COMPLETED,
    /** 登録の完了の要求のリンクを拒否した（U3。リンクの確かめの失敗・入力の誤りは記録しない）。 */
    REGISTRATION_FAILED,
    /** 管理者の印を付けた（Intent 260930-user-admin の U3、契約 C6。成功と失敗は結果で分ける）。 */
    USER_ADMIN_GRANTED,
    /** 管理者の印を外した（Intent 260930-user-admin の U3）。 */
    USER_ADMIN_REVOKED,
    /** 利用を止めた（Intent 260930-user-admin の U3）。 */
    USER_SUSPENDED,
    /** 停止を解いた（Intent 260930-user-admin の U3）。 */
    USER_RESUMED,
    /** ログインの失敗回数を戻した（Intent 260930-user-admin の U3）。 */
    LOGIN_FAILURES_RESET,
    /** 起動時に初期管理者を作成した（Intent 261004-safety-carryover の FR1.5。操作した人は空、接続元は {@code system}）。 */
    INITIAL_ADMIN_CREATED,
    /**
     * 起動時に初期管理者を救済した（Intent 261004-safety-carryover の FR1.5・FR1.6a。操作した人は空、接続元は {@code system}、
     * 当たった条件は {@code rejection_kind} の列）。
     */
    INITIAL_ADMIN_RESCUED
}
