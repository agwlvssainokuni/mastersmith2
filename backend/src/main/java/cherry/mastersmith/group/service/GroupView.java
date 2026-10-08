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
package cherry.mastersmith.group.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 作ったグループの値（作成の応答の元。FS の 2.1 の手順 4）。JPA のエンティティを業務処理の外へ出さないための写し。
 *
 * @param groupId グループの ID
 * @param name 名前
 * @param createdAt 作成の日時
 * @param updatedAt 更新の日時
 */
public record GroupView(long groupId, String name, Instant createdAt, Instant updatedAt) {

    /** 値が null でないことを確かめる。 */
    public GroupView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
