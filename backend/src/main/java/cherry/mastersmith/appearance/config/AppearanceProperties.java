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
package cherry.mastersmith.appearance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * インスタンスの見た目の設定（{@code mastersmith.appearance.*}、U8）。
 *
 * <p>どちらの値も任意の文字列のまま受け、既定値を持たせず、{@code @Validated}・検証の注釈を付けず、列挙に結び付けない。値が
 * 無い・不正でも Spring の結び付けで起動を止めず、判定は {@code appearance.service} の {@code AppearanceResolver} で行うため
 * （BR1.7、NFR9.1）。
 *
 * @param brandColor ブランドカラーの設定の値（無ければ {@code null} または空）
 * @param fontFamily フォントファミリーの設定の値（無ければ {@code null} または空）
 */
@ConfigurationProperties(AppearanceProperties.PREFIX)
public record AppearanceProperties(String brandColor, String fontFamily) {

    /** 設定の接頭辞。 */
    public static final String PREFIX = "mastersmith.appearance";

    /** ブランドカラーの設定の名前。 */
    public static final String BRAND_COLOR = PREFIX + ".brand-color";

    /** フォントファミリーの設定の名前。 */
    public static final String FONT_FAMILY = PREFIX + ".font-family";
}
