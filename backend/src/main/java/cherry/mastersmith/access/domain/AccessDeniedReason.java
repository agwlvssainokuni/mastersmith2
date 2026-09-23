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

import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import java.util.Optional;
import org.springframework.security.core.AuthenticationException;

/**
 * アクセス拒否の理由（BR3.1、BR3.2）。監査ログの記録項目（U4）の理由にそろえる。
 *
 * <p>有効期限切れ（U2 の {@link TokenFailureReason#TOKEN_EXPIRED}）は、通常の利用で起きるため出来事にしない（BR3.2）。
 * そのため、U2 の区分からの変換は「出来事にしない」を表せる {@link Optional} で返す。
 */
public enum AccessDeniedReason {
    /** 管理者でない利用者の要求（403）。 */
    NOT_ADMIN,
    /** アクセストークンが無い要求（401）。 */
    TOKEN_MISSING,
    /** アクセストークンの形式の誤り（401）。 */
    TOKEN_MALFORMED,
    /** アクセストークンの署名・方式の不一致（401）。 */
    TOKEN_INVALID,
    /** アクセストークンの利用者が DB にいない（401）。 */
    USER_NOT_FOUND;

    /**
     * U2 のアクセストークンの失敗の区分を、アクセス拒否の理由に変える。
     *
     * @param reason U2 の失敗の区分
     * @return アクセス拒否の理由。有効期限切れ（出来事にしない）なら空
     */
    public static Optional<AccessDeniedReason> of(TokenFailureReason reason) {
        return switch (reason) {
            case TOKEN_MALFORMED -> Optional.of(TOKEN_MALFORMED);
            case TOKEN_INVALID -> Optional.of(TOKEN_INVALID);
            case USER_NOT_FOUND -> Optional.of(USER_NOT_FOUND);
            case TOKEN_EXPIRED -> Optional.empty();
        };
    }

    /**
     * 401 の認証の失敗の例外を、アクセス拒否の理由に変える。
     *
     * <p>U2 の {@link TokenAuthenticationException} ならその区分を、そうでなければ（トークンが無い要求）
     * {@link #TOKEN_MISSING} を返す。
     *
     * @param exception 認証の失敗の例外（null でもよい）
     * @return アクセス拒否の理由。有効期限切れ（出来事にしない）なら空
     */
    public static Optional<AccessDeniedReason> of(AuthenticationException exception) {
        if (exception instanceof TokenAuthenticationException token) {
            return of(token.reason());
        }
        return Optional.of(TOKEN_MISSING);
    }
}
