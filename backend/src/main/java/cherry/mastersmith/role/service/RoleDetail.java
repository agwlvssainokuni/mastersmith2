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

import java.time.Instant;
import java.util.Objects;

/**
 * ロール1件の値（作成の応答と1件の読み取りの元。契約 C7 の Role、BR3.1・BR3.7）。JPA のエンティティを業務処理の外へ出さないための写し。
 *
 * @param roleId ロールの ID
 * @param name 名前
 * @param createdAt 作成の日時
 * @param updatedAt 更新の日時
 */
public record RoleDetail(long roleId, String name, Instant createdAt, Instant updatedAt) {

    /** 値が null でないことを確かめる。 */
    public RoleDetail {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** ID だけを出す（計画の D-5）。 */
    @Override
    public String toString() {
        return "RoleDetail[roleId=" + roleId + "]";
    }
}
