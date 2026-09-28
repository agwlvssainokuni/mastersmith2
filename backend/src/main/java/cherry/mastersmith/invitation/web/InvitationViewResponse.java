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
package cherry.mastersmith.invitation.web;

import cherry.mastersmith.invitation.service.InvitationView;

/**
 * リンクの確かめの応答（契約 C6 の InvitationView）。文字列にするとメールアドレスを伏せる。
 *
 * @param email 招待先のメールアドレス
 * @param language 招待の言語
 */
public record InvitationViewResponse(String email, String language) {

    /**
     * 業務処理の結果から作る。
     *
     * @param view 結果
     * @return 応答
     */
    public static InvitationViewResponse from(InvitationView view) {
        return new InvitationViewResponse(view.email(), view.language());
    }

    /** メールアドレスを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "InvitationViewResponse[email=***, language=" + language + "]";
    }
}
