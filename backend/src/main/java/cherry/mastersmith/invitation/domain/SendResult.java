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

/**
 * 招待メールの送信の結果（{@code entities.md} の Invitation.sendResult、BR4.3・BR4.4）。
 *
 * <p>確定の時点で {@link #PENDING}（送信中・結果不明）とし、送信の後に {@link #SENT} か {@link #FAILED} に書き換える。API（契約 C5）へは
 * {@link #PENDING} を {@code FAILED} として渡し、契約の値（{@code SENT}・{@code FAILED}）は変えない。招待の状態
 * {@link InvitationState#PENDING} とは別の値の集合である。
 */
public enum SendResult {
    /** 送信中・結果不明（送信の途中でアプリが止まると、このまま残る）。 */
    PENDING,
    /** 送った（受け手が受け付けた）。 */
    SENT,
    /** 送れなかった（種類によらない）。 */
    FAILED;

    /**
     * 送信の成否から、記録する結果を返す。
     *
     * @param sent 送れたなら true
     * @return {@link #SENT} か {@link #FAILED}
     */
    public static SendResult of(boolean sent) {
        return sent ? SENT : FAILED;
    }

    /**
     * API（契約 C5）で渡す値を返す（{@link #PENDING} は {@code FAILED}。BR4.4）。
     *
     * @return {@code SENT} か {@code FAILED}
     */
    public String apiValue() {
        return this == SENT ? SENT.name() : FAILED.name();
    }
}
