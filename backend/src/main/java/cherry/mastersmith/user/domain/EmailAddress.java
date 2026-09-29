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
import java.util.regex.Pattern;

/** メールアドレスの決まり（BR2.1）。DB を使わない純粋な関数。 */
public final class EmailAddress {

    /** メールアドレスの長さの上限（表の列の長さ）。 */
    public static final int MAX_LENGTH = 254;

    private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private EmailAddress() {}

    /**
     * 前後の空白を除き、小文字にそろえる。
     *
     * <p>除く空白は {@link String#trim()} の範囲（U+0020 以下の文字）に限る。全角の空白（U+3000）は除かない。
     *
     * @param email 入力されたメールアドレス
     * @return そろえた値（null なら null）
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * そろえた値がメールアドレスの形式に合うかを返す（初期管理者の設定の検査に使う）。
     *
     * @param normalized {@link #normalize(String)} でそろえた値
     * @return 形式に合えば true
     */
    public static boolean isValid(String normalized) {
        return normalized != null
                && normalized.length() <= MAX_LENGTH
                && FORMAT.matcher(normalized).matches();
    }

    /**
     * ログに載せるための伏せ字を返す（Intent 260929-log-deps-cleanup の FR1.2）。
     *
     * <p>形は「ローカル部の先頭の1文字＋{@code ***}＋{@code @}＋ドメイン」（例 {@code admin@example.com} →
     * {@code a***@example.com}）。ローカル部とドメインは最後の {@code @} で分け、先頭の1文字は Unicode のコードポイントで数える
     * （サロゲートペアを割らない）。
     *
     * <ul>
     *   <li>値が null・空のときは null を返す（呼び出し側はキーを載せない）。
     *   <li>{@code @} を含まない、またはローカル部が空のときは {@code ***} を返す（値のどこも見せない）。
     * </ul>
     *
     * <p>ローカル部の2文字目以降を伏せるため、メールアドレスそのものは出ない。このため、伏せ字をアプリのログに載せることは
     * {@code project.md} の Forbidden（メールアドレスをアプリのログに含めない）に反しないとみなす（要件の追加の質問 F1: A）。
     * ただし、ローカル部が1文字のとき（例 {@code a@example.com}）は、先頭の1文字でローカル部が全部見える。要件のレビューの
     * 指摘 R-03 を受け入れた扱いで、形は変えない（計画の依頼者の答え Q-A: A）。
     *
     * @param email メールアドレス（そろえた値を想定する）
     * @return 伏せ字（null・空なら null）
     */
    public static String mask(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0) {
            return "***";
        }
        // at は 1 以上のため、先頭の1コードポイントは @ より前で終わる（@ は下位サロゲートにならない）。
        int firstEnd = email.offsetByCodePoints(0, 1);
        return email.substring(0, firstEnd) + "***" + email.substring(at);
    }
}
