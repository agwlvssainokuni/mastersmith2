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

import cherry.mastersmith.auth.domain.AccessTokenValue;
import java.time.Instant;

/**
 * 発行したアクセストークン。
 *
 * @param value トークンの値（文字列化で伏せる）
 * @param expiresAt 有効期限
 */
public record IssuedAccessToken(AccessTokenValue value, Instant expiresAt) {}
