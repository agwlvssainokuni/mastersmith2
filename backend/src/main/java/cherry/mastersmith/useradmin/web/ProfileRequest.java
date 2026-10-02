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
package cherry.mastersmith.useradmin.web;

import cherry.mastersmith.user.service.ProfileCommand;

/**
 * 管理者による氏名と言語の変更の要求（{@code PUT /api/admin/users/{userId}/profile}、契約 C3 の ProfileRequest、BR5.1・BR5.4）。
 *
 * <p>項目は氏名と言語の2つだけを文字列で受け、Bean Validation の注釈を付けない（まとめて検証し、項目ごとの誤りを返すため）。
 * メールアドレス・パスワード・テーマ・文字の大きさ・管理者の印・停止の状態・失敗回数の項目は持たない（本文に入れても読み捨てる）。
 * 文字列にすると氏名を伏せる。
 *
 * @param displayName 氏名
 * @param language 言語（{@code ja}・{@code en}）
 */
public record ProfileRequest(String displayName, String language) {

    /**
     * 業務処理への入力にする。
     *
     * @return 氏名と言語の入力（検証の前の値）
     */
    ProfileCommand toCommand() {
        return new ProfileCommand(displayName, language);
    }

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "ProfileRequest[displayName=***, language=" + language + "]";
    }
}
