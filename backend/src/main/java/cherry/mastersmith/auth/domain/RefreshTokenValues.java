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
package cherry.mastersmith.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** リフレッシュトークンの値の生成とハッシュ（BR5.1、NFR5.2）。 */
public final class RefreshTokenValues {

    /** 値の元の乱数のバイト数（256 ビット）。 */
    public static final int RANDOM_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenValues() {}

    /**
     * 暗号学的な乱数 32 バイトを、URL で使える Base64（埋め草なし）にした値を作る。
     *
     * @return 新しい値
     */
    public static RefreshTokenValue generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return new RefreshTokenValue(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * 値の SHA-256 のハッシュを返す。DB にはこれだけを保存する。
     *
     * @param value 値
     * @return ハッシュ（32 バイト）
     */
    public static byte[] hash(RefreshTokenValue value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.value().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 を使えません", e);
        }
    }
}
