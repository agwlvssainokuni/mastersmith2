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
package cherry.mastersmith.businessdata.domain;

import java.util.Objects;

/**
 * 部分一致（{@code LIKE ? ESCAPE '!'}）に渡す値を作る純粋な関数（BR4.3、security-design.md の 2節）。値の中の {@code !}・{@code %}・
 * {@code _} に {@code !} を前置し、前後に {@code %} を付ける。バックスラッシュは文字のまま。
 */
public final class ContainsPattern {

    /** エスケープの文字。 */
    public static final char ESCAPE = '!';

    private ContainsPattern() {}

    /**
     * 部分一致の値を作る。
     *
     * @param value 検索の値
     * @return {@code LIKE} に渡す値
     */
    public static String of(String value) {
        Objects.requireNonNull(value, "value は必須です");
        StringBuilder pattern = new StringBuilder(value.length() + 8).append('%');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ESCAPE || c == '%' || c == '_') {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }
}
