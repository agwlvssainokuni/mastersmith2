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
package cherry.mastersmith.mail.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * メールアドレスの決まり（BR3.1）。宛先の確かめと、差出人の設定の点検（BR1.3）で使う純粋な関数。
 *
 * <p>既存の利用者のメールアドレスの決まり（{@code user.domain.EmailAddress}）と同じ決まりだが、部品 Mail は他の部品に依存しない
 * （components.md の depends_on が空）ため、U1 の中に同じ決まりを持つ（機能設計の U1 R-02、依頼者が受け入れた）。
 */
public final class MailAddressRule {

    /** メールアドレスの長さの上限。 */
    public static final int MAX_LENGTH = 254;

    /** 「空白と @ を含まない文字列 @ 空白と @ を含まない文字列 . 空白と @ を含まない文字列」の形。 */
    private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private MailAddressRule() {}

    /**
     * 正規化済み（前後の空白が無く小文字）で、254 文字まで・形式に合うメールアドレスかを返す。
     *
     * <p>前後の空白の判定は既存の決まりと同じく {@link String#trim()} の範囲（U+0020 以下の文字）とする。
     *
     * @param value 確かめる値
     * @return 決まりに合えば true
     */
    public static boolean isValid(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= MAX_LENGTH
                && value.equals(value.trim().toLowerCase(Locale.ROOT))
                && FORMAT.matcher(value).matches();
    }
}
