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
package cherry.mastersmith.auth.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * テストの中で作った鍵で、アクセストークン（JWT）の正しいもの・改ざんしたもの・方式を変えたものを作るテストの補助。
 *
 * <p>鍵はテストの実行のたびに作るか、テストが設定した鍵（Base64）を渡す。本物の鍵はソースに書かない。
 */
public final class AuthTestTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    /**
     * 署名鍵を指定して作る。
     *
     * @param base64Key 署名鍵（Base64）
     */
    public AuthTestTokens(String base64Key) {
        this.key = Base64.getDecoder().decode(base64Key);
    }

    /**
     * 利用者ID・発行時刻・有効期限を載せた HS256 のトークンを作る。
     *
     * @param userId 利用者ID
     * @param issuedAt 発行時刻
     * @param expiresAt 有効期限
     * @return トークン
     */
    public String hs256(long userId, Instant issuedAt, Instant expiresAt) {
        return mac(JWSAlgorithm.HS256, key, claims(userId, issuedAt, expiresAt));
    }

    /**
     * 別の鍵で署名した HS256 のトークンを作る。
     *
     * @param userId 利用者ID
     * @param issuedAt 発行時刻
     * @param expiresAt 有効期限
     * @return トークン
     */
    public String hs256WithOtherKey(long userId, Instant issuedAt, Instant expiresAt) {
        return mac(JWSAlgorithm.HS256, randomBytes(32), claims(userId, issuedAt, expiresAt));
    }

    /**
     * 署名の最後の1文字を変えたトークンを作る。
     *
     * @param userId 利用者ID
     * @param issuedAt 発行時刻
     * @param expiresAt 有効期限
     * @return トークン
     */
    public String tamperedSignature(long userId, Instant issuedAt, Instant expiresAt) {
        return tamper(hs256(userId, issuedAt, expiresAt));
    }

    /**
     * 署名の無い（方式が none の）トークンを作る。
     *
     * @param userId 利用者ID
     * @param issuedAt 発行時刻
     * @param expiresAt 有効期限
     * @return トークン
     */
    public static String algNone(long userId, Instant issuedAt, Instant expiresAt) {
        return new PlainJWT(claims(userId, issuedAt, expiresAt)).serialize();
    }

    /**
     * 別の方式（HS512、別の 64 バイトの鍵）で署名したトークンを作る。
     *
     * @param userId 利用者ID
     * @param issuedAt 発行時刻
     * @param expiresAt 有効期限
     * @return トークン
     */
    public static String hs512(long userId, Instant issuedAt, Instant expiresAt) {
        return mac(JWSAlgorithm.HS512, randomBytes(64), claims(userId, issuedAt, expiresAt));
    }

    /**
     * 公開鍵の方式（RS256、テストの中で作った鍵）で署名したトークンを作る。
     *
     * @param userId 利用者ID
     * @param issuedAt 発行時刻
     * @param expiresAt 有効期限
     * @return トークン
     */
    public static String rs256(long userId, Instant issuedAt, Instant expiresAt) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims(userId, issuedAt, expiresAt));
            jwt.sign(new RSASSASigner(pair.getPrivate()));
            return jwt.serialize();
        } catch (NoSuchAlgorithmException | JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * トークンの署名（3つ目の部分）の最後の1文字を変える。
     *
     * @param token トークン
     * @return 改ざんしたトークン
     */
    public static String tamper(String token) {
        char last = token.charAt(token.length() - 1);
        char replaced = last == 'A' ? 'B' : 'A';
        return token.substring(0, token.length() - 1) + replaced;
    }

    private static JWTClaimsSet claims(long userId, Instant issuedAt, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .subject(Long.toString(userId))
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
    }

    private static String mac(JWSAlgorithm algorithm, byte[] secret, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
