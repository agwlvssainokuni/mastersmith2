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
package cherry.mastersmith.user.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 初期管理者を救済した出来事（アプリの中の知らせ、保存しない。Intent 261004-safety-carryover の FR1.2・FR1.5）。
 *
 * <p>UserAccount の救済の操作が、停止を解く・印を付ける・パスワードを置き換えた同じトランザクションの中で出す。auth の受け手が同じ
 * トランザクションで失敗回数とリフレッシュトークンを扱い、AuditLog が確定の後に受けて監査イベントを1件追記する。
 * メールアドレス・パスワード・パスワードのハッシュを持たない（FR1.5・NFR1）。
 *
 * @param userId 救済した利用者 ID
 * @param conditions 当たった条件の集まり（空でない。宣言の順の変えられない写しで持つ）
 * @param occurredAt 出来事が起きた日時（注入できる時計の値）
 */
public record InitialAdminRescuedEvent(long userId, Set<InitialAdminRescueCondition> conditions, Instant occurredAt) {

    /** 必須の値と、条件が空でないことを確かめ、条件を変えられない写しにする。 */
    public InitialAdminRescuedEvent {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("救済の条件が空です");
        }
        // contains(null) は Set.of の集まりで NullPointerException を投げるため、要素を順に見る。
        if (conditions.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("conditions に null が含まれます");
        }
        conditions = Collections.unmodifiableSet(EnumSet.copyOf(conditions));
    }
}
