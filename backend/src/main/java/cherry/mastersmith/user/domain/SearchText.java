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
package cherry.mastersmith.user.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * 利用者の一覧の検索の文字（Intent 260930-user-admin の U3、BR1.3〜BR1.5・BR7.4）。
 *
 * <p>要求の q の生の値を包む。画面入出力の層の引数の時点からこの型で受け、業務処理と UserAccount へもこの型のまま渡す。文字列にすると
 * 値を伏せる（メソッドの呼び出しの追跡が各層の引数を文字列にするため）。空白の除去・長さの判定・検索のパターンへの変換はこの record の
 * 中で行い、変換の途中の値を {@code String} で返す公開のメソッドを持たない（検索のパターンも伏せる型 {@link RedactedText} で返す）。
 *
 * <ul>
 *   <li>前後の空白は Unicode の White_Space の文字（氏名の保存と同じ範囲、{@link DisplayName#strip(String)}）で除く（BR1.4）
 *   <li>除いた後のコードポイントの数が {@value #MAX_CODE_POINTS} を超えれば長すぎる（BR1.4）
 *   <li>検索のパターンは、除いた後の値を {@link Locale#ROOT} で小文字にし、{@code \}・{@code %}・{@code _} を {@code \} でエスケープし、
 *       前後に {@code %} を付ける（BR1.5、R-07）
 * </ul>
 *
 * @param value 要求の q の生の値
 */
public record SearchText(String value) {

    /** 検索の文字の長さの上限（前後の空白を除いた後のコードポイントの数。BR1.4）。 */
    public static final int MAX_CODE_POINTS = 254;

    /** 検索のパターンのエスケープの文字（問い合わせの {@code escape '\'} と同じ）。 */
    public static final char ESCAPE = '\\';

    /** 値が null でないことを確かめる（検証はしない。BR1.3）。 */
    public SearchText {
        Objects.requireNonNull(value, "value");
    }

    /**
     * 前後の空白を除いた後が空かを返す（空なら検索なし。BR1.4）。
     *
     * @return 空なら true
     */
    public boolean isBlank() {
        return DisplayName.strip(value).isEmpty();
    }

    /**
     * 前後の空白を除いた後のコードポイントの数が上限を超えるかを返す（BR1.4）。
     *
     * @return 長すぎれば true
     */
    public boolean isTooLong() {
        String stripped = DisplayName.strip(value);
        return stripped.codePointCount(0, stripped.length()) > MAX_CODE_POINTS;
    }

    /**
     * 検索のパターン（部分一致、大文字と小文字を区別しない比べ方の右辺）を返す（BR1.5）。
     *
     * @return 小文字にそろえ、{@code \}・{@code %}・{@code _} をエスケープし、前後に {@code %} を付けた値
     */
    public RedactedText likePattern() {
        String lower = DisplayName.strip(value).toLowerCase(Locale.ROOT);
        StringBuilder pattern = new StringBuilder(lower.length() + 2).append('%');
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == ESCAPE || c == '%' || c == '_') {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return new RedactedText(pattern.append('%').toString());
    }

    /** 値を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "SearchText[***]";
    }
}
