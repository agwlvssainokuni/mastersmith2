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
package cherry.mastersmith.role.repository;

import java.time.Instant;
import java.util.Objects;

/**
 * ロール1件の射影（契約 C7 の Role。BR3.7）。
 *
 * @param roleId ロールの ID
 * @param name 名前
 * @param createdAt 作成の日時
 * @param updatedAt 更新の日時
 */
public record RoleView(long roleId, String name, Instant createdAt, Instant updatedAt) {

    /** 値が null でないことを確かめる。 */
    public RoleView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
