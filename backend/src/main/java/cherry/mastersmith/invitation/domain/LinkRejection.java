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
 * リンクを拒否した理由（内部だけ。BR7.6、{@code entities.md} の LinkRejection）。監査の失敗の理由にだけ使い、応答は理由によらず
 * {@code REGISTRATION_LINK_INVALID} にする（BR7.5）。
 */
public enum LinkRejection {
    /** トークンの形が合わない・ハッシュが無い（送り直しで古くなった・改ざん・定期の削除で消えたを含む）。 */
    INVITATION_NOT_FOUND,
    /** 登録を完了した招待。 */
    INVITATION_ALREADY_USED,
    /** 取り消した招待。 */
    INVITATION_CANCELLED,
    /** 置き換え済み、または期限切れの招待中。 */
    INVITATION_EXPIRED,
    /** 招待は有効だが、完了の時点で同じメールアドレスの利用者がいる（BR7.4）。 */
    EMAIL_ALREADY_REGISTERED
}
