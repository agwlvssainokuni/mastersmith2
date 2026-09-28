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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 招待を使える設定かの判定の結果（BR1.4、{@code entities.md} の InvitationAvailability）。起動のときに1回決まり、動いている間は
 * 変わらない。設定の値そのものは持たない。
 *
 * @param enabled 使えるなら true
 * @param unavailableReasons 使えない理由（使えるときは空。並びは BASE_URL_NOT_CONFIGURED、SMTP_NOT_CONFIGURED の順）
 */
public record InvitationAvailability(boolean enabled, List<UnavailableReason> unavailableReasons) {

    /** 理由の一覧を変えられない写しにし、使えるかと理由の有無が合うことを確かめる。 */
    public InvitationAvailability {
        unavailableReasons = List.copyOf(Objects.requireNonNull(unavailableReasons, "unavailableReasons"));
        if (enabled != unavailableReasons.isEmpty()) {
            throw new IllegalArgumentException("使えるときは理由を持たず、使えないときは理由を1つ以上持ちます");
        }
    }

    /**
     * 2つの設定の有無から判定する。
     *
     * @param baseUrlUsable ベース URL に使える値があるなら true
     * @param smtpConfigured メールの送信の設定があるなら true
     * @return 判定の結果
     */
    public static InvitationAvailability of(boolean baseUrlUsable, boolean smtpConfigured) {
        List<UnavailableReason> reasons = new ArrayList<>();
        if (!baseUrlUsable) {
            reasons.add(UnavailableReason.BASE_URL_NOT_CONFIGURED);
        }
        if (!smtpConfigured) {
            reasons.add(UnavailableReason.SMTP_NOT_CONFIGURED);
        }
        return new InvitationAvailability(reasons.isEmpty(), reasons);
    }
}
