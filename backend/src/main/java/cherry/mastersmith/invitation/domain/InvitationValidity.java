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
package cherry.mastersmith.invitation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 招待の有効の判定と拒否の理由の決め方（BR3.3・BR7.6、NFR1.3）。DB も時計も使わない純粋な関数（今は呼び出し元が注入した時計から渡す）。
 *
 * <p>有効は「状態が {@link InvitationState#PENDING} かつ 今 &lt; 有効期限」だけで、有効期限の時刻ちょうどは無効とする。
 */
public final class InvitationValidity {

    private InvitationValidity() {}

    /**
     * 有効かを返す。
     *
     * @param state 状態
     * @param expiresAt 有効期限
     * @param now 今
     * @return 有効なら true
     */
    public static boolean isValid(InvitationState state, Instant expiresAt, Instant now) {
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");
        return state == InvitationState.PENDING && now.isBefore(expiresAt);
    }

    /**
     * 期限切れの招待中かを返す（一覧の {@code expired}。BR5.3）。
     *
     * @param state 状態
     * @param expiresAt 有効期限
     * @param now 今
     * @return 状態が招待中で、今が有効期限以後なら true
     */
    public static boolean isExpired(InvitationState state, Instant expiresAt, Instant now) {
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");
        return state == InvitationState.PENDING && !now.isBefore(expiresAt);
    }

    /**
     * 見つかった招待を拒否する理由を返す（BR7.6）。
     *
     * @param state 状態
     * @param expiresAt 有効期限
     * @param now 今
     * @return 拒否の理由（有効なら空）
     */
    public static Optional<LinkRejection> rejectionOf(InvitationState state, Instant expiresAt, Instant now) {
        Objects.requireNonNull(state, "state");
        if (isValid(state, expiresAt, now)) {
            return Optional.empty();
        }
        return Optional.of(
                switch (state) {
                    case COMPLETED -> LinkRejection.INVITATION_ALREADY_USED;
                    case CANCELLED -> LinkRejection.INVITATION_CANCELLED;
                    case REPLACED, PENDING -> LinkRejection.INVITATION_EXPIRED;
                });
    }
}
