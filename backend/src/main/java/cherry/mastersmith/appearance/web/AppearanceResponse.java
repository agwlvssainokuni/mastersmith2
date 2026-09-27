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
package cherry.mastersmith.appearance.web;

import cherry.mastersmith.appearance.service.ResolvedAppearance;

/**
 * {@code GET /api/appearance} の応答（契約 C7）。2項目だけを持ち、値は許される値の小文字の名前（BR3.1・BR3.4、NFR4.5）。
 * 業務処理が持つ列挙の値から写すため、設定された元の文字列は応答に届かない。
 *
 * @param brandColor ブランドカラー（{@code blue}・{@code green}・{@code purple}・{@code orange}）
 * @param fontFamily フォントファミリー（{@code sans}・{@code serif}）
 */
public record AppearanceResponse(String brandColor, String fontFamily) {

    /**
     * 解決した値から作る。
     *
     * @param appearance 解決した値
     * @return 応答
     */
    public static AppearanceResponse from(ResolvedAppearance appearance) {
        return new AppearanceResponse(
                appearance.brandColor().value(), appearance.fontFamily().value());
    }
}
