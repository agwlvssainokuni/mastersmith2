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

import jakarta.validation.constraints.NotBlank;

/**
 * ログインの要求（{@code POST /api/auth/login}）。空は 400 / {@code VALIDATION_FAILED}（BR2.2）。長さなどの形式はログインでは
 * 検査しない。
 *
 * @param email メールアドレス
 * @param password パスワード（文字列化で伏せる）
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    /** パスワードを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
