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
package cherry.mastersmith.group.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * グループの名前の値（BR1.1〜BR1.4、NFR1.11）。作るときに正規化と検証を行い、正しいものだけが作れる。DB を使わない純粋な関数である。
 *
 * <ul>
 *   <li>前後から Unicode の White_Space の文字（全角の空白を含む）を取り除いてから判定し、取り除いた値を保存する。内側の空白は
 *       取り除かない・まとめない（BR1.1）
 *   <li>取り除いた後が空なら {@link GroupNameValidation.Reason#INVALID_BLANK}（BR1.1）
 *   <li>取り除いた後のコードポイントの数が {@value #MAX_CODE_POINTS} を超えれば {@link GroupNameValidation.Reason#INVALID_TOO_LONG}
 *       （BR1.2。ちょうどは受け付ける）
 *   <li>制御文字（一般の区分 Cc。改行・タブを含む）と、行・段落の区切りの文字（Zl・Zp。改行として扱う）が1つでもあれば
 *       {@link GroupNameValidation.Reason#INVALID_CONTROL_CHARACTER}（BR1.3）
 * </ul>
 *
 * <p>理由は上の順で最初に当たったものだけを返す。
 *
 * <p>重なりの判定の鍵（{@link #key()}）は、言語に依らない小文字化（{@link Locale#ROOT}）で作る。全角と半角は変換しない（BR1.4）。
 * 鍵を作るのはこのクラスだけで、書く前の重なりの判定と保存（列 {@code name_key}）の両方がこの値を使う（機能設計の承認の場の直し
 * R-04）。名前の列は UTF-16 の 128 単位、鍵の列は 256 単位で、どちらも上限の名前で収まる（R-03。性質ベースのテストで確かめる）。
 *
 * <p>グループの名前は個人に関する値として扱わない（NFR 要件のまとめの確認）。
 */
public final class GroupName {

    /** 名前の長さの上限（前後の空白を取り除いた後のコードポイントの数。BR1.2）。 */
    public static final int MAX_CODE_POINTS = 64;

    private final String value;

    private final String key;

    private GroupName(String value) {
        this.value = value;
        this.key = value.toLowerCase(Locale.ROOT);
    }

    /**
     * 入力された名前を正規化して検証する（BR1.1〜BR1.3）。
     *
     * @param raw 入力された名前（null でもよい）
     * @return 正しければ名前、正しくなければ理由
     */
    public static GroupNameValidation parse(String raw) {
        String stripped = strip(raw);
        if (stripped == null || stripped.isEmpty()) {
            return new GroupNameValidation.Invalid(GroupNameValidation.Reason.INVALID_BLANK);
        }
        if (stripped.codePointCount(0, stripped.length()) > MAX_CODE_POINTS) {
            return new GroupNameValidation.Invalid(GroupNameValidation.Reason.INVALID_TOO_LONG);
        }
        if (stripped.codePoints().anyMatch(GroupName::isControl)) {
            return new GroupNameValidation.Invalid(GroupNameValidation.Reason.INVALID_CONTROL_CHARACTER);
        }
        return new GroupNameValidation.Valid(new GroupName(stripped));
    }

    /**
     * 名前の値を返す（前後の空白を取り除いた値）。
     *
     * @return 名前
     */
    public String value() {
        return value;
    }

    /**
     * 大文字と小文字を区別しない比べのための鍵を返す（列 {@code name_key} に保存する値。BR1.4）。
     *
     * @return 鍵
     */
    public String key() {
        return key;
    }

    /**
     * 前の名前と文字どおり同じかを返す（BR1.5。大文字と小文字だけが違えば同じではない）。
     *
     * @param current 今の名前（保存されている値）
     * @return 文字どおり同じなら true
     */
    public boolean sameAs(String current) {
        return value.equals(current);
    }

    /**
     * 前後から Unicode の White_Space の文字を取り除く。
     *
     * @param raw 入力された名前（null でもよい）
     * @return 取り除いた値（null は null のまま）
     */
    static String strip(String raw) {
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
     * Unicode の White_Space の性質を持つ文字かを返す（{@link Character#isWhitespace(int)} とは範囲が違うため自前で持つ。
     * {@code user.domain.DisplayName} と同じ範囲）。
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

    /**
     * 名前に使えない文字（制御文字 Cc と、行・段落の区切りの文字 Zl・Zp）かを返す（BR1.3）。
     *
     * @param codePoint コードポイント
     * @return 使えない文字なら true
     */
    static boolean isControl(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupName name && value.equals(name.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
