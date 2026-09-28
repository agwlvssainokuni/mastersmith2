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
import cherry.mastersmith.user.domain.FieldError;
import java.util.List;

/** 招待の結果（契約 C5 の POST）。想定内の失敗は例外にせず、この型で返す（HTTP の状態は画面入出力の層が決める）。 */
public sealed interface InviteResult {

    /**
     * 招待した（送信に失敗しても招待は確定している。BR4.5）。
     *
     * @param summary 招待の要約
     */
    record Invited(InvitationSummary summary) implements InviteResult {}

    /**
     * 入力の誤り（BR1.1・BR1.2）。
     *
     * @param errors 項目ごとの誤り
     */
    record Invalid(List<FieldError> errors) implements InviteResult {}

    /**
     * 招待を使えない設定（BR1.5）。
     *
     * @param reasons 使えない理由
     */
    record NotConfigured(List<UnavailableReason> reasons) implements InviteResult {}

    /** 登録済みのメールアドレス（BR2.1）。 */
    record EmailRegistered() implements InviteResult {}

    /**
     * 同じメールアドレスの期限内の招待中がある（BR2.2・BR2.5）。
     *
     * @param invitationId その招待の ID
     * @param page その招待が載る一覧のページ
     */
    record AlreadyPending(long invitationId, int page) implements InviteResult {}
}
