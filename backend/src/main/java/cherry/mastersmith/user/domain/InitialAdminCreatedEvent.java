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
import java.util.Objects;

/**
 * 初期管理者を作成した出来事（アプリの中の知らせ、保存しない。Intent 261004-safety-carryover の FR1.4・FR1.5）。
 *
 * <p>初期管理者の自動作成が、作成の確定の後に出し、AuditLog が受けて監査イベントを1件追記する。メールアドレス・パスワード・
 * パスワードのハッシュを持たない（FR1.5・NFR1）。
 *
 * @param userId 作った利用者 ID
 * @param occurredAt 出来事が起きた日時（注入できる時計の値）
 */
public record InitialAdminCreatedEvent(long userId, Instant occurredAt) {

    /** 必須の値を確かめる。 */
    public InitialAdminCreatedEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
