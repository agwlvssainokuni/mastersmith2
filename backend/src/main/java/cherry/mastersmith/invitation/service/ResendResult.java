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
package cherry.mastersmith.invitation.service;

import cherry.mastersmith.invitation.domain.UnavailableReason;
import java.util.List;

/** 送り直しの結果（契約 C5 の POST resend）。 */
public sealed interface ResendResult {

    /**
     * 送り直した（送信に失敗しても新しいトークンと有効期限は確定している。BR4.5）。
     *
     * @param summary 招待の要約
     */
    record Resent(InvitationSummary summary) implements ResendResult {}

    /**
     * 招待を使えない設定（何も変えていない。BR1.5）。
     *
     * @param reasons 使えない理由
     */
    record NotConfigured(List<UnavailableReason> reasons) implements ResendResult {}

    /** 対象が無い・招待中でない（BR6.3）。 */
    record NotFound() implements ResendResult {}
}
