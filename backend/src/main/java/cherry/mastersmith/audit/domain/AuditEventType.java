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
 * 260925-user-management の U2、招待と登録は同じ Intent の U3、契約 C8）。
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
    REGISTRATION_FAILED
}
