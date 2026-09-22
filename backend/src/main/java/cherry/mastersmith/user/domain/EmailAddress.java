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
}
