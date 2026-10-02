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
package cherry.mastersmith.invitation.lock;

import cherry.mastersmith.common.persistence.RowLockUnavailableException;
import cherry.mastersmith.invitation.domain.Invitation;
import cherry.mastersmith.invitation.domain.InvitationEmail;
import cherry.mastersmith.invitation.domain.InvitationState;
import java.util.Optional;

/**
 * 招待の行の排他の読み取り（Spring Data の独自の断片。{@link InvitationRepository} が継ぐ）。待ちの上限は 3 秒（既存の
 * {@code LoginAttemptStateRepository} と同じ）。
 *
 * <p>Intent 260930-user-admin の U3（{@code security-design.md} 7.2 の E2〜E4）で、Spring Data の {@code @Lock} の問い合わせから、
 * EntityManager を直接使う実装（{@code invitation.lock.InvitationLockQueriesImpl}。追跡の対象の層の外）へ移した。メソッドの名前・引数・戻り値は移す前と同じ。排他の待ちの
 * 上限切れ・行き詰まりは実装の本体の中で受け、例外の連なり（文に招待の行の値が入りうる）を外へ出さず、値を含まない
 * {@link RowLockUnavailableException} を投げる（想定外の誤り。応答は今までどおり 500）。
 */
public interface InvitationLockQueries {

    /**
     * 同じメールアドレスの招待中を行の排他つきで読む（BR2.2・BR2.4）。
     *
     * @param email 正規化したメールアドレス
     * @param state 招待中（{@link InvitationState#PENDING}）
     * @return 招待中の招待（無ければ空。常に1件まで）
     * @throws RowLockUnavailableException 排他を取れなかったとき
     */
    Optional<Invitation> findByEmailAndStateForUpdate(InvitationEmail email, InvitationState state);

    /**
     * 招待を ID で行の排他つきで読む（送り直し・取り消し。BR6.4）。
     *
     * @param invitationId 招待の ID
     * @return 招待（無ければ空）
     * @throws RowLockUnavailableException 排他を取れなかったとき
     */
    Optional<Invitation> findByIdForUpdate(long invitationId);

    /**
     * 招待をトークンのハッシュで行の排他つきで読む（登録の完了。BR6.4・BR7.3）。
     *
     * @param tokenHash トークンのハッシュ
     * @return 招待（無ければ空）
     * @throws RowLockUnavailableException 排他を取れなかったとき
     */
    Optional<Invitation> findByTokenHashForUpdate(byte[] tokenHash);
}
