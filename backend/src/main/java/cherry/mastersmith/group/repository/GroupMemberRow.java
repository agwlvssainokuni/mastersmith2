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
package cherry.mastersmith.group.repository;

import java.time.Instant;
import java.util.Objects;

/**
 * 詳細のメンバーの行の投影（利用者 ID と足した日時だけ。氏名・メールアドレスは {@code user.service} のまとめて読む口が伏せる型で返す。
 * BR7.4・BR9.1）。
 *
 * @param userId 利用者 ID
 * @param addedAt 足した日時
 */
public record GroupMemberRow(long userId, Instant addedAt) {

    /** 日時が null でないことを確かめる。 */
    public GroupMemberRow {
        Objects.requireNonNull(addedAt, "addedAt");
    }
}
