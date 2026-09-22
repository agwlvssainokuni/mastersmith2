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

import java.nio.charset.StandardCharsets;

/**
 * パスワードの決まり（BR1.3、BR2.8、NFR2.2）。DB を使わない純粋な関数。
 *
 * <p>作成時は「12 文字以上（コードポイントで数える）かつ UTF-8 で 72 バイト以内」。ログインでは長さの規則は検査せず、72 バイトの
 * 確認だけに使う（bcrypt が扱えるのは 72 バイトまでのため）。
 */
public final class PasswordPolicy {

    /** 作成時の最小の文字数（コードポイント）。 */
    public static final int MIN_LENGTH = 12;

    /** UTF-8 のバイト数の上限。 */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    /**
     * 文字数（コードポイント）が最小以上かを返す。
     *
     * @param password パスワード
     * @return 12 文字以上なら true
     */
    public static boolean hasMinimumLength(String password) {
        return password.codePointCount(0, password.length()) >= MIN_LENGTH;
    }

    /**
     * UTF-8 のバイト数が上限以内かを返す。
     *
     * @param password パスワード
     * @return 72 バイト以内なら true
     */
    public static boolean fitsMaxBytes(String password) {
        return utf8Bytes(password) <= MAX_BYTES;
    }

    /**
     * UTF-8 のバイト数を返す。
     *
     * @param password パスワード
     * @return バイト数
     */
    public static int utf8Bytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 作成時の規則を満たすかを返す。
     *
     * @param password パスワード
     * @return 12 文字以上かつ 72 バイト以内なら true
     */
    public static boolean isAcceptableForCreation(String password) {
        return hasMinimumLength(password) && fitsMaxBytes(password);
    }
}
