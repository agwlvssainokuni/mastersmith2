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
package cherry.mastersmith.user.service;

/**
 * 利用者の要約（UserAccount の外へ渡す形。パスワードのハッシュを含まない。ADR-001、BR5.5）。
 *
 * <p>氏名と表示の設定は文字列で持つ（{@code auth} は {@code user.domain} のエンティティに依存しないため。値は小文字の文字列）。
 * 文字列化ではメールアドレスと氏名を伏せる（メソッドの呼び出しの追跡が業務処理の戻り値を文字列にするため。既存の利用者の氏名の初期値は
 * メールアドレス）。
 *
 * @param userId 利用者ID
 * @param email メールアドレス
 * @param admin 管理者か
 * @param displayName 氏名
 * @param language 言語（{@code ja}・{@code en}）
 * @param theme テーマ（{@code light}・{@code dark}・{@code system}）
 * @param fontSize 文字の大きさ（{@code sm}・{@code md}・{@code lg}）
 */
public record UserSummary(
        long userId, String email, boolean admin, String displayName, String language, String theme, String fontSize) {

    /** メールアドレスと氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "UserSummary[userId=" + userId + ", email=***, admin=" + admin + ", displayName=***, language="
                + language + ", theme=" + theme + ", fontSize=" + fontSize + "]";
    }
}
