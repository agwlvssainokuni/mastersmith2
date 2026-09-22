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
package cherry.mastersmith.user.service;

import java.util.Optional;

/**
 * 照合の結果（ハッシュを含まない。ADR-001）。
 *
 * @param email 前後の空白を除き小文字にそろえたメールアドレス
 * @param user 利用者の要約（いなければ null）
 * @param matched パスワードが一致したか（利用者がいない・72 バイトを超えるときは false）
 */
public record PasswordVerification(String email, UserSummary user, boolean matched) {

    /**
     * 利用者の要約を返す。
     *
     * @return 利用者の要約（いなければ空）
     */
    public Optional<UserSummary> userSummary() {
        return Optional.ofNullable(user);
    }
}
