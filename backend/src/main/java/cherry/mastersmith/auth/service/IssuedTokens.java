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
package cherry.mastersmith.auth.service;

import cherry.mastersmith.auth.domain.RefreshTokenValue;
import cherry.mastersmith.user.service.UserSummary;
import java.time.Duration;

/**
 * ログイン・更新で発行したトークンと利用者の要約。トークンの値は文字列化で伏せる（値の型が {@code ***} を返す）。
 *
 * @param accessToken アクセストークン
 * @param refreshToken リフレッシュトークンの値（Cookie で渡す）
 * @param refreshTokenTtl リフレッシュトークンの有効期限の長さ（Cookie の Max-Age）
 * @param user 利用者の要約
 */
public record IssuedTokens(
        IssuedAccessToken accessToken, RefreshTokenValue refreshToken, Duration refreshTokenTtl, UserSummary user) {}
