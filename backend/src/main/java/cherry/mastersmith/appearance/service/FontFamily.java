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

/** フォントファミリーの許される値（BR1.2、契約 C7 の {@code fontFamily}）。既定は {@link #SANS}（BR1.3）。 */
public enum FontFamily {
    /** ゴシック体（既定）。 */
    SANS,
    /** 明朝体。 */
    SERIF;

    private static final String ALLOWED_VALUES =
            Arrays.stream(values()).map(FontFamily::value).collect(Collectors.joining(", "));

    /**
     * 画面へ渡す名前（小文字）を返す。
     *
     * @return 小文字の名前（例 {@code sans}）
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 既定の値を返す。
     *
     * @return {@link #SANS}
     */
    public static FontFamily defaultValue() {
        return SANS;
    }

    /**
     * 許される値の一覧を、警告のログに出す形の文字列で返す。
     *
     * @return {@code sans, serif}
     */
    public static String allowedValues() {
        return ALLOWED_VALUES;
    }
}
