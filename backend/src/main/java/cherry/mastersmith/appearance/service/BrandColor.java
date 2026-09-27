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
package cherry.mastersmith.appearance.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** ブランドカラーの許される値（BR1.1、契約 C7 の {@code brandColor}）。既定は {@link #BLUE}（BR1.3）。 */
public enum BrandColor {
    /** 青（既定）。 */
    BLUE,
    /** 緑。 */
    GREEN,
    /** 紫。 */
    PURPLE,
    /** 橙。 */
    ORANGE;

    private static final String ALLOWED_VALUES =
            Arrays.stream(values()).map(BrandColor::value).collect(Collectors.joining(", "));

    /**
     * 画面へ渡す名前（小文字）を返す。
     *
     * @return 小文字の名前（例 {@code blue}）
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 既定の値を返す。
     *
     * @return {@link #BLUE}
     */
    public static BrandColor defaultValue() {
        return BLUE;
    }

    /**
     * 許される値の一覧を、警告のログに出す形の文字列で返す。
     *
     * @return {@code blue, green, purple, orange}
     */
    public static String allowedValues() {
        return ALLOWED_VALUES;
    }
}
