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
package cherry.mastersmith.auth.web;

import cherry.mastersmith.auth.service.IssuedTokens;
import java.time.Instant;

/**
 * ログインと更新の応答。リフレッシュトークンは本文に入れず、Cookie で渡す（計画の C2）。
 *
 * @param accessToken アクセストークン（文字列化で伏せる）
 * @param expiresAt アクセストークンの有効期限
 * @param user ログイン中の利用者
 */
public record TokenResponse(String accessToken, Instant expiresAt, CurrentUserResponse user) {

    /**
     * 発行したトークンから応答を作る。
     *
     * @param tokens 発行したトークン
     * @return 応答
     */
    public static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(
                tokens.accessToken().value().value(),
                tokens.accessToken().expiresAt(),
                new CurrentUserResponse(tokens.user().email(), tokens.user().admin()));
    }

    /** アクセストークンを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, expiresAt=" + expiresAt + ", user=" + user + "]";
    }
}
