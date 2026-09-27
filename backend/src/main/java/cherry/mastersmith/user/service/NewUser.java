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

import cherry.mastersmith.user.domain.FontSize;
import cherry.mastersmith.user.domain.Language;
import cherry.mastersmith.user.domain.Password;
import cherry.mastersmith.user.domain.Theme;

/**
 * 利用者の作成の入力（契約 C2）。値は決まりに合うことを前提とし、呼び出し元が先に同じ決まり（氏名の {@code DisplayName}、表示の設定の
 * 値、{@code PasswordPolicy}）で入力を検証する（BR5.1）。文字列化ではメールアドレス・氏名・パスワードを伏せる。
 *
 * @param email メールアドレス（そろえる前の値でよい。作成の中で小文字にそろえる）
 * @param displayName 氏名（前後の空白を除く前の値でよい。作成の中で除く）
 * @param password パスワード（作成時の規則に合う値）
 * @param language 言語
 * @param theme テーマ
 * @param fontSize 文字の大きさ
 * @param admin 管理者か（招待からの作成は false、初期管理者は true）
 */
public record NewUser(
        String email,
        String displayName,
        Password password,
        Language language,
        Theme theme,
        FontSize fontSize,
        boolean admin) {

    /** メールアドレス・氏名・パスワードを伏せて文字列にする。 */
    @Override
    public String toString() {
        return "NewUser[email=***, displayName=***, password=***, language=" + language + ", theme=" + theme
                + ", fontSize=" + fontSize + ", admin=" + admin + "]";
    }
}
