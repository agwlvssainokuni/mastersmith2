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
 * 利用者が DB にいない）の両方で使う。
 */
public enum AuditFailureReason {
    /** 利用者がいない。 */
    USER_NOT_FOUND,
    /** パスワードの誤り。 */
    PASSWORD_MISMATCH,
    /** ロック中。 */
    ACCOUNT_LOCKED,
    /** 管理者でない利用者の要求（403）。 */
    NOT_ADMIN,
    /** アクセストークンが無い要求（401）。 */
    TOKEN_MISSING,
    /** アクセストークンの形式の誤り（401）。 */
    TOKEN_MALFORMED,
    /** アクセストークンの署名・方式の不一致（401）。 */
    TOKEN_INVALID
}
