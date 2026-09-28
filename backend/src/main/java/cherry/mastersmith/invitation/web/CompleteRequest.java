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
 * 登録の完了の要求（{@code POST /api/registration/complete}、契約 C6 の CompleteRequest）。項目はすべて文字列で受け、トークンは本文だけで
 * 受け取る（BR3.5）。文字列にするとトークン・氏名・パスワードを伏せる。
 *
 * @param token 招待のトークン
 * @param displayName 氏名
 * @param password パスワード
 * @param passwordConfirmation 確かめの値
 * @param language 言語
 * @param theme テーマ
 * @param fontSize 文字の大きさ
 */
public record CompleteRequest(
        String token,
        String displayName,
        String password,
        String passwordConfirmation,
        String language,
        String theme,
        String fontSize) {

    /** トークン・氏名・パスワードを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "CompleteRequest[token=***, displayName=***, password=***, passwordConfirmation=***, language="
                + language + ", theme=" + theme + ", fontSize=" + fontSize + "]";
    }
}
