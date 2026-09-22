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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 認証の設定（{@code mastersmith.auth.*}）。値だけを持ち、署名鍵の検査は {@code SigningKeyProvider} で行う。
 *
 * <p>範囲は起動時に Bean Validation で検証し、範囲外なら起動を止める（NFR6.3）。既定値は application.yaml の既定値と同じ
 * （{@code AuthPropertiesDefaultsTest} で確かめる）。
 *
 * @param signingKey アクセストークンの署名鍵（Base64。必須・秘密）
 * @param accessTokenTtl アクセストークンの有効期限（既定 5 分）
 * @param refreshTokenTtl リフレッシュトークンの有効期限（既定 24 時間）
 * @param lock アカウントロックの設定
 * @param refreshTokenCleanup 使い終わったリフレッシュトークンの削除の設定
 */
@Validated
@ConfigurationProperties("mastersmith.auth")
public record AuthProperties(
        String signingKey,
        @DefaultValue("5m") @NotNull @DurationMin(nanos = 1) Duration accessTokenTtl,

        @DefaultValue("24h") @NotNull @DurationMin(nanos = 1)
        Duration refreshTokenTtl,

        @DefaultValue @NotNull @Valid Lock lock,
        @DefaultValue @NotNull @Valid RefreshTokenCleanup refreshTokenCleanup) {

    /**
     * アカウントロックの設定（{@code mastersmith.auth.lock.*}）。
     *
     * @param threshold ロックするまでの連続失敗回数（既定 5、1 以上）
     * @param duration ロックの時間（既定 30 分、正の値）
     */
    public record Lock(
            @DefaultValue("5") @Min(1) int threshold,

            @DefaultValue("30m") @NotNull @DurationMin(nanos = 1)
            Duration duration) {}

    /**
     * 使い終わったリフレッシュトークンの削除の設定（{@code mastersmith.auth.refresh-token-cleanup.*}）。
     *
     * @param retention 無効・期限切れの行を、期限からどれだけ残すか（既定 7 日、0 以上）
     * @param cron 削除を行う時刻（Spring の cron の形。既定は毎日 3 時 30 分）
     */
    public record RefreshTokenCleanup(
            @DefaultValue("7d") @NotNull @DurationMin(seconds = 0)
            Duration retention,

            @DefaultValue("0 30 3 * * *") @NotBlank String cron) {}

    /** 署名鍵の値を伏せて文字列にする（メソッドの呼び出しの追跡やログに値を出さないため）。 */
    @Override
    public String toString() {
        return "AuthProperties[signingKey=***, accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl="
                + refreshTokenTtl + ", lock=" + lock + ", refreshTokenCleanup=" + refreshTokenCleanup + "]";
    }
}
