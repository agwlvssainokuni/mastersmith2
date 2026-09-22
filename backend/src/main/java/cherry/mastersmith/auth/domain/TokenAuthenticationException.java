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
package cherry.mastersmith.auth.domain;

import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * アクセストークンの認証の失敗（U3 との約束。U3 の security-design 3章）。
 *
 * <p>Spring Security の {@code OAuth2AuthenticationException}（したがって {@code AuthenticationException}）の子で、区分を
 * {@link #reason()} で返す。Bearer の検証の途中で起きても認証の入口の処理へ届く。メッセージには区分だけを入れ、トークンの値を
 * 入れない。
 */
public class TokenAuthenticationException extends OAuth2AuthenticationException {

    private static final long serialVersionUID = 1L;

    private final TokenFailureReason reason;

    /**
     * 区分を指定して作る。
     *
     * @param reason 失敗の区分
     */
    public TokenAuthenticationException(TokenFailureReason reason) {
        super(
                new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN,
                        Objects.requireNonNull(reason, "reason").name(),
                        null),
                reason.name());
        this.reason = reason;
    }

    /**
     * 失敗の区分を返す。
     *
     * @return 失敗の区分
     */
    public TokenFailureReason reason() {
        return reason;
    }
}
