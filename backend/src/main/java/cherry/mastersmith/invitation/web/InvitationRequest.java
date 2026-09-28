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

/**
 * 招待の要求（{@code POST /api/admin/invitations}、契約 C5 の InvitationRequest）。項目はすべて文字列で受け、Bean Validation の注釈を
 * 付けない（まとめて検証して項目ごとの誤りを返すため）。文字列にするとメールアドレスを伏せる。
 *
 * @param email 招待先のメールアドレス
 * @param language 招待の言語
 */
public record InvitationRequest(String email, String language) {

    /** メールアドレスを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "InvitationRequest[email=***, language=" + language + "]";
    }
}
