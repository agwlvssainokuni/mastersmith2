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
package cherry.mastersmith.user.web;

import cherry.mastersmith.user.domain.Preferences;

/**
 * プリファレンスの応答（契約 C4 の Preferences）。メールアドレス・管理者か・パスワードのハッシュを含めない（BR3.1）。テーマ
 * {@code system} は解決せずにそのまま返す。文字列化では氏名を伏せる。
 *
 * @param displayName 氏名
 * @param language 言語
 * @param theme テーマ
 * @param fontSize 文字の大きさ
 */
public record PreferencesResponse(String displayName, String language, String theme, String fontSize) {

    /**
     * 4つの組から応答を作る。
     *
     * @param preferences 4つの組
     * @return 応答
     */
    public static PreferencesResponse from(Preferences preferences) {
        return new PreferencesResponse(
                preferences.displayName(),
                preferences.language().value(),
                preferences.theme().value(),
                preferences.fontSize().value());
    }

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "PreferencesResponse[displayName=***, language=" + language + ", theme=" + theme + ", fontSize="
                + fontSize + "]";
    }
}
