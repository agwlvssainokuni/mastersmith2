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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 招待のトークンの値（BR3.1・BR3.2、NFR1.1・NFR1.6、{@code security-design.md} 4節・5節）。保存しない。
 *
 * <p>暗号学的な乱数 32 バイト（256 ビット）を URL で使える Base64（埋め草なし、43 文字）にした文字列。内部DB には UTF-8 の SHA-256
 * （32 バイト）だけを保存する（既存のリフレッシュトークンと同じ作り方）。文字列にすると値を伏せる（メソッドの呼び出しの追跡が引数と
 * 戻り値を文字列にするため）。
 *
 * @param value トークンの文字列（43 文字、URL で使える Base64 の文字だけ）
 */
public record InvitationToken(String value) {

    /** 元の乱数のバイト数（256 ビット）。 */
    public static final int RANDOM_BYTES = 32;

    /** トークンの文字数（32 バイトの URL で使える Base64、埋め草なし）。 */
    public static final int LENGTH = 43;

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{" + LENGTH + "}");

    /** 形を確かめる（値は例外のメッセージに載せない）。 */
    public InvitationToken {
        if (!isWellFormed(value)) {
            throw new IllegalArgumentException("招待のトークンの形が違います");
        }
    }

    /**
     * 新しいトークンを作る。
     *
     * @param random 暗号学的に安全な乱数
     * @return 新しいトークン
     */
    public static InvitationToken generate(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return new InvitationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * 入力されたトークンの形を確かめる。DB を引かない（BR3.2）。
     *
     * @param raw 入力された値（null でもよい）
     * @return 43 文字で URL で使える Base64 の文字だけならトークン（合わなければ空）
     */
    public static Optional<InvitationToken> parse(String raw) {
        return isWellFormed(raw) ? Optional.of(new InvitationToken(raw)) : Optional.empty();
    }

    private static boolean isWellFormed(String raw) {
        return raw != null && FORMAT.matcher(raw).matches();
    }

    /**
     * トークンの UTF-8 の SHA-256 のハッシュを返す。内部DB にはこれだけを保存する。
     *
     * @return ハッシュ（32 バイト）
     */
    public byte[] hash() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 を使えません", e);
        }
    }

    /** 値を伏せて文字列にする。 */
    @Override
    public String toString() {
        return "InvitationToken[****]";
    }
}
