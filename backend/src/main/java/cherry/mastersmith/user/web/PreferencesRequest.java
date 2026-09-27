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

/**
 * プリファレンスの保存の要求（{@code PUT /api/me/preferences}、契約 C4 の Preferences）。
 *
 * <p>項目はすべて文字列で受け、Bean Validation の注釈を付けない（まとめて検証し、項目ごとの誤りを返すため。
 * {@code nfr-design/security-design.md} 3節）。利用者 ID・メールアドレスの項目は持たない（本文に入れても読み捨てる。NFR4.3）。
 * 文字列化では氏名を伏せる。
 *
 * @param displayName 氏名
 * @param language 言語（{@code ja}・{@code en}）
 * @param theme テーマ（{@code light}・{@code dark}・{@code system}）
 * @param fontSize 文字の大きさ（{@code sm}・{@code md}・{@code lg}）
 */
public record PreferencesRequest(String displayName, String language, String theme, String fontSize) {

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "PreferencesRequest[displayName=***, language=" + language + ", theme=" + theme + ", fontSize="
                + fontSize + "]";
    }
}
