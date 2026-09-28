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
package cherry.mastersmith.invitation.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * 招待のリンクのベース URL の決まり（BR1.3、NFR1.4）。DB を使わない純粋な関数。
 *
 * <ul>
 *   <li>無い・空白だけ: 使える値が無い（警告なし。招待を使わない使い方を許す）
 *   <li>前後の空白を除いた値が、http・https の絶対 URL でない、ホストが無い、問い合わせ（{@code ?}）・フラグメント（{@code #}）・
 *       利用者情報（{@code user@}）を含む、URL として解釈できない: 使える値が無い（警告あり）
 *   <li>それ以外: 使える値。パスは許し、末尾の {@code /} は除く
 * </ul>
 *
 * <p>既存のエラー応答の type の URL の扱い（{@code ProblemBaseUrlResolver}）は変えない。
 */
public final class BaseUrlRule {

    private BaseUrlRule() {}

    /**
     * 判定の結果。
     *
     * @param value 使える値（末尾の {@code /} を除いた値。無ければ null）
     * @param invalid 値があったが形が合わなかった（起動のときに警告を出す）なら true
     */
    public record Check(String value, boolean invalid) {

        /**
         * 使える値を返す。
         *
         * @return 使える値（無ければ空）
         */
        public Optional<String> usable() {
            return Optional.ofNullable(value);
        }

        /** 値を出さずに文字列にする（設定の値をログに出さないため）。 */
        @Override
        public String toString() {
            return "BaseUrlRule.Check[usable=" + (value != null) + ", invalid=" + invalid + "]";
        }
    }

    /**
     * 設定の値を判定する。
     *
     * @param raw 設定の値（null でもよい）
     * @return 判定の結果
     */
    public static Check evaluate(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Check(null, false);
        }
        String trimmed = raw.strip();
        if (trimmed.indexOf('?') >= 0 || trimmed.indexOf('#') >= 0) {
            return new Check(null, true);
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return new Check(null, true);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https"))
                || uri.getHost() == null
                || uri.getHost().isEmpty()
                || uri.getRawUserInfo() != null) {
            return new Check(null, true);
        }
        String value = trimmed;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return new Check(value, false);
    }
}
