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

import java.util.Optional;

/**
 * 氏名の決まり（BR1.1〜BR1.6、NFR9.2）。DB を使わない純粋な関数で、プリファレンスの保存と利用者の作成（後の単位の登録の完了を含む）が
 * 同じ関数を使う。
 *
 * <ul>
 *   <li>前後から Unicode の White_Space の文字を除いてから判定し、除いた値を保存する。内側の空白は除かない・まとめない（BR1.1）
 *   <li>除いた後が空なら {@link FieldErrorReason#REQUIRED}（BR1.2）
 *   <li>除いた後のコードポイントの数が 254 を超えれば {@link FieldErrorReason#TOO_LONG}（BR1.3）
 *   <li>制御文字（Cc）か見えない書式の文字（Cf）があれば {@link FieldErrorReason#INVALID_CHARACTER}（BR1.4）
 *   <li>Unicode の正規化・大文字小文字の変換をしない（BR1.5）
 * </ul>
 *
 * <p>理由は上の順で最初に当たったものだけを返す。
 */
public final class DisplayName {

    /** 氏名の長さの上限（前後の空白を除いた後のコードポイントの数）。 */
    public static final int MAX_CODE_POINTS = 254;

    private DisplayName() {}

    /**
     * 前後から Unicode の White_Space の文字を除く。
     *
     * @param raw 入力された氏名（null でもよい）
     * @return 除いた値（null は null のまま）
     */
    public static String strip(String raw) {
        if (raw == null) {
            return null;
        }
        int start = 0;
        int end = raw.length();
        while (start < end && isWhiteSpace(raw.codePointAt(start))) {
            start += Character.charCount(raw.codePointAt(start));
        }
        while (end > start && isWhiteSpace(raw.codePointBefore(end))) {
            end -= Character.charCount(raw.codePointBefore(end));
        }
        return raw.substring(start, end);
    }

    /**
     * 氏名が決まりに合うかを判定する。
     *
     * @param raw 入力された氏名（null でもよい。前後の空白を除く前の値）
     * @return 合わなければ理由（合えば空）
     */
    public static Optional<FieldErrorReason> check(String raw) {
        String stripped = strip(raw);
        if (stripped == null || stripped.isEmpty()) {
            return Optional.of(FieldErrorReason.REQUIRED);
        }
        if (stripped.codePointCount(0, stripped.length()) > MAX_CODE_POINTS) {
            return Optional.of(FieldErrorReason.TOO_LONG);
        }
        if (stripped.codePoints().anyMatch(DisplayName::isControlOrFormat)) {
            return Optional.of(FieldErrorReason.INVALID_CHARACTER);
        }
        return Optional.empty();
    }

    /**
     * 決まりに合う氏名を、前後の空白を除いた値にして返す。合わなければ想定外の誤りとする（利用者の作成の前提。BR5.1）。
     *
     * @param raw 入力された氏名
     * @return 前後の空白を除いた値
     * @throws IllegalArgumentException 決まりに合わないとき（値は例外のメッセージに載せない）
     */
    public static String requireValid(String raw) {
        Optional<FieldErrorReason> reason = check(raw);
        if (reason.isPresent()) {
            throw new IllegalArgumentException("氏名が決まりに合いません: " + reason.get());
        }
        return strip(raw);
    }

    /**
     * Unicode の White_Space の性質を持つ文字かを返す（{@link Character#isWhitespace(int)} とは範囲が違うため自前で持つ）。
     *
     * @param codePoint コードポイント
     * @return White_Space の文字なら true
     */
    static boolean isWhiteSpace(int codePoint) {
        return (codePoint >= 0x0009 && codePoint <= 0x000D)
                || codePoint == 0x0020
                || codePoint == 0x0085
                || codePoint == 0x00A0
                || codePoint == 0x1680
                || (codePoint >= 0x2000 && codePoint <= 0x200A)
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202F
                || codePoint == 0x205F
                || codePoint == 0x3000;
    }

    private static boolean isControlOrFormat(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT;
    }
}
