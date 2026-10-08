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
package cherry.mastersmith.role.service;

import java.util.Objects;

/** ロール1件の読み取りの結果（FS の 2.4a、BR3.7）。 */
public sealed interface RoleDetailResult permits RoleDetailResult.Found, RoleDetailResult.NotFound {

    /**
     * 見つかった。
     *
     * @param role ロール
     */
    record Found(RoleDetail role) implements RoleDetailResult {

        /** 値が null でないことを確かめる。 */
        public Found {
            Objects.requireNonNull(role, "role");
        }
    }

    /** ロールがいない（404 {@code ROLE_NOT_FOUND}。読み取りのため監査なし）。 */
    record NotFound() implements RoleDetailResult {}
}
