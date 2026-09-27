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

import java.util.Locale;
import java.util.function.Function;

/**
 * 見た目の設定の値の判定（BR1.1〜BR1.5、NFR9.2・NFR9.8）。副作用の無い純粋な関数で、どの入力（{@code null}・空・空白だけ・任意の
 * 文字列）でも例外を投げない。
 *
 * <p>項目ごとに別々に、次の順で判定する。
 *
 * <ol>
 *   <li>{@code null} → 既定・警告なし
 *   <li>前後の空白（{@link String#strip()}。全角の空白・タブを含む）を除いて空 → 既定・警告なし（BR1.3）
 *   <li>{@link Locale#ROOT} で小文字にして許される値の名前と一致 → その値・警告なし（BR1.1・BR1.2。実行環境の言語の設定に
 *       左右されない）
 *   <li>一致しない → 既定・警告あり（BR1.4）
 * </ol>
 */
public final class AppearanceResolver {

    private AppearanceResolver() {}

    /**
     * ブランドカラーを判定する。
     *
     * @param raw 設定の値（{@code null} 可）
     * @return 判定の結果
     */
    public static Resolution<BrandColor> resolveBrandColor(String raw) {
        return resolve(raw, BrandColor.values(), BrandColor::value, BrandColor.defaultValue());
    }

    /**
     * フォントファミリーを判定する。
     *
     * @param raw 設定の値（{@code null} 可）
     * @return 判定の結果
     */
    public static Resolution<FontFamily> resolveFontFamily(String raw) {
        return resolve(raw, FontFamily.values(), FontFamily::value, FontFamily.defaultValue());
    }

    private static <T> Resolution<T> resolve(String raw, T[] allowed, Function<T, String> nameOf, T defaultValue) {
        if (raw == null) {
            return new Resolution<>(defaultValue, false);
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new Resolution<>(defaultValue, false);
        }
        for (T candidate : allowed) {
            if (nameOf.apply(candidate).equals(normalized)) {
                return new Resolution<>(candidate, false);
            }
        }
        return new Resolution<>(defaultValue, true);
    }
}
