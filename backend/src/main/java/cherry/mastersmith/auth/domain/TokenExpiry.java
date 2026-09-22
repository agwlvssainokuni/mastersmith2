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

import java.time.Instant;

/** トークンの有効期限の判定（BR4.2、BR5.3）。DB を使わない純粋な関数。 */
public final class TokenExpiry {

    private TokenExpiry() {}

    /**
     * 現在時刻が有効期限より前のときだけ有効とする（期限ちょうどは無効）。
     *
     * @param now 現在時刻
     * @param expiresAt 有効期限
     * @return 有効なら true
     */
    public static boolean isValid(Instant now, Instant expiresAt) {
        return now.isBefore(expiresAt);
    }
}
