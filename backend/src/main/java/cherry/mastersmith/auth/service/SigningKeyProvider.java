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
package cherry.mastersmith.auth.service;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * アクセストークンの署名鍵（NFR3.3、NFR6.2）。{@code mastersmith.auth.signing-key} を Base64 から戻し、無い・戻せない・
 * 32 バイト未満なら起動を止める。例外のメッセージには設定の名前と必要な長さだけを載せ、値を載せない。
 */
@Component
public class SigningKeyProvider {

    /** 鍵の最小のバイト数（256 ビット）。 */
    public static final int MIN_KEY_BYTES = 32;

    /** 鍵の問題を示すメッセージ（値を含まない）。 */
    static final String INVALID_KEY_MESSAGE =
            "アクセストークンの署名鍵 mastersmith.auth.signing-key（環境変数 MASTERSMITH_AUTH_SIGNING_KEY）が設定されていないか、"
                    + "Base64 で 32 バイト以上の値ではありません。32 バイト以上の乱数を Base64 にした値を設定してください"
                    + "（例: openssl rand -base64 32）";

    private final SecretKey secretKey;

    /**
     * 設定から鍵を作る。
     *
     * @param properties 認証の設定
     * @throws IllegalStateException 鍵が無い・Base64 でない・32 バイト未満のとき
     */
    public SigningKeyProvider(AuthProperties properties) {
        this.secretKey = decode(properties.signingKey());
    }

    private static SecretKey decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(INVALID_KEY_MESSAGE);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            // 例外の元（入力の一部を含みうる）はつなげない。
            throw new IllegalStateException(INVALID_KEY_MESSAGE);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(INVALID_KEY_MESSAGE);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * 署名鍵を返す。
     *
     * @return HMAC-SHA256 の鍵
     */
    public SecretKey secretKey() {
        return secretKey;
    }

    /** 鍵を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "SigningKeyProvider[***]";
    }
}
