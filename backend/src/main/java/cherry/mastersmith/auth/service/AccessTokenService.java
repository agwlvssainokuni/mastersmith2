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

import cherry.mastersmith.auth.domain.AccessTokenValue;
import cherry.mastersmith.auth.domain.TokenAuthenticationException;
import cherry.mastersmith.auth.domain.TokenExpiry;
import cherry.mastersmith.auth.domain.TokenFailureReason;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * アクセストークン（HS256 の JWT）の発行と検証（BR4.1〜BR4.3、NFR3.2）。
 *
 * <p>載せる項目は利用者ID（{@code sub}）・発行時刻（{@code iat}）・有効期限（{@code exp}）だけ。検証は HS256 だけを受け付け、
 * トークンが示す方式を信用しない。有効期限は注入した時計で比べ、ずれの許容は 0（期限ちょうどで無効）。検証の仕組みは起動時に
 * 1つ作って使い回す。
 */
@Service
public class AccessTokenService {

    private final NimbusJwtEncoder encoder;

    private final NimbusJwtDecoder decoder;

    private final Duration ttl;

    private final Clock clock;

    /**
     * 発行と検証の仕組みを作る。
     *
     * @param signingKeyProvider 署名鍵
     * @param properties 認証の設定
     * @param clock 時計
     */
    public AccessTokenService(SigningKeyProvider signingKeyProvider, AuthProperties properties, Clock clock) {
        this.encoder =
                NimbusJwtEncoder.withSecretKey(signingKeyProvider.secretKey()).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(signingKeyProvider.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .validateType(false)
                .build();
        this.decoder.setJwtValidator(this::validateExpiry);
        this.ttl = properties.accessTokenTtl();
        this.clock = clock;
    }

    /**
     * アクセストークンを発行する（時刻は秒の単位にそろえる）。
     *
     * @param userId 利用者ID
     * @return 発行したトークンと有効期限
     */
    public IssuedAccessToken issue(long userId) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));
        return new IssuedAccessToken(new AccessTokenValue(jwt.getTokenValue()), expiresAt);
    }

    /**
     * アクセストークンを検証し、利用者IDを返す。
     *
     * @param token トークン
     * @return 利用者ID
     * @throws TokenAuthenticationException 形式の誤り（{@code TOKEN_MALFORMED}）、署名・方式の不一致（{@code TOKEN_INVALID}）、
     *     有効期限切れ（{@code TOKEN_EXPIRED}）のとき
     */
    public long verify(AccessTokenValue token) {
        JWT parsed;
        try {
            parsed = JWTParser.parse(token.value());
        } catch (ParseException e) {
            throw new TokenAuthenticationException(TokenFailureReason.TOKEN_MALFORMED);
        }
        if (!(parsed instanceof SignedJWT signed)
                || !JWSAlgorithm.HS256.equals(signed.getHeader().getAlgorithm())) {
            throw new TokenAuthenticationException(TokenFailureReason.TOKEN_INVALID);
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token.value());
        } catch (JwtValidationException e) {
            throw new TokenAuthenticationException(TokenFailureReason.TOKEN_EXPIRED);
        } catch (BadJwtException e) {
            throw new TokenAuthenticationException(TokenFailureReason.TOKEN_INVALID);
        } catch (JwtException e) {
            throw new TokenAuthenticationException(TokenFailureReason.TOKEN_MALFORMED);
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new TokenAuthenticationException(TokenFailureReason.TOKEN_MALFORMED);
        }
    }

    private OAuth2TokenValidatorResult validateExpiry(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt != null && TokenExpiry.isValid(clock.instant(), expiresAt)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "TOKEN_EXPIRED", null));
    }
}
