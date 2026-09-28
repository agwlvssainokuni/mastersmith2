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
 * 招待の状態（保存する4つ。{@code entities.md} の Invitation.state、{@code functional-spec.md} 1.1）。
 *
 * <p>期限切れは保存せず、{@link #PENDING} のうち時計の今が有効期限以後のものとして読むたびに決める（{@link InvitationValidity}）。
 * 終わった3つ（{@link #COMPLETED}・{@link #CANCELLED}・{@link #REPLACED}）からは動かない。
 */
public enum InvitationState {
    /** 招待中（期限切れを含む）。 */
    PENDING,
    /** 登録を完了した。 */
    COMPLETED,
    /** 取り消した。 */
    CANCELLED,
    /** 期限切れのまま同じメールアドレスへ新しく招待され、置き換わった（BR2.4）。 */
    REPLACED
}
