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

import cherry.mastersmith.user.domain.DisplayTimeZoneInput;
import tools.jackson.databind.JsonNode;

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
 * @param displayTimeZone 表示のタイムゾーン（Intent 261102-timezone-ui-polish の U2、BR2.3）。3 状態を見分けるため JSON の
 *     ノードで受ける: 項目が無い（Java の {@code null}）・明示の {@code null}（{@code NullNode}）・値。文字列化で中身を伏せる
 */
public record PreferencesRequest(
        String displayName, String language, String theme, String fontSize, JsonNode displayTimeZone) {

    /**
     * 表示のタイムゾーンの 3 状態を業務処理の入力にする（検証の前の値。名前の検証は業務処理の層の名前の門が行う）。
     *
     * @return 項目が無ければ Keep、明示の null なら Clear、文字列なら Text、ほかの種類なら NotText（種類の名前だけを持つ）
     */
    public DisplayTimeZoneInput displayTimeZoneInput() {
        if (displayTimeZone == null) {
            return new DisplayTimeZoneInput.Keep();
        }
        if (displayTimeZone.isNull()) {
            return new DisplayTimeZoneInput.Clear();
        }
        if (displayTimeZone.isString()) {
            return new DisplayTimeZoneInput.Text(displayTimeZone.asString());
        }
        return new DisplayTimeZoneInput.NotText(displayTimeZone.getNodeType().name());
    }

    /** 氏名を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "PreferencesRequest[displayName=***, language=" + language + ", theme=" + theme + ", fontSize="
                + fontSize + ", displayTimeZone=" + (displayTimeZone == null ? "absent" : "***") + "]";
    }
}
