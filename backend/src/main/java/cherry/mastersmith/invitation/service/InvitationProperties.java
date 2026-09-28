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
package cherry.mastersmith.invitation.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 招待の設定（{@code mastersmith.invitation.*}、BR1.6、{@code infrastructure-specification.md} 3.2）。値だけを持つ。
 *
 * <p>範囲の確かめ（有効期限は 1 時間以上で 1 時間で割り切れる長さ、保存の日数は 1 日以上で 1 日で割り切れる長さ）は
 * {@link InvitationSettings} が起動のときに行い、合わなければ項目の名前だけの例外で起動を止める。形の誤り（{@code Duration} として
 * 読めない値）は Spring の結び付けで起動が止まる。既定値は application.yaml の既定値と同じ。
 *
 * @param validity 招待の有効期限の長さ（既定 24 時間）
 * @param retention 終わった招待・期限切れの招待中を残す長さ（既定 90 日）
 * @param cleanup 定期の削除の設定
 */
@ConfigurationProperties("mastersmith.invitation")
public record InvitationProperties(
        @DefaultValue("24h") Duration validity,
        @DefaultValue("90d") Duration retention,
        @DefaultValue Cleanup cleanup) {

    /**
     * 定期の削除の設定（{@code mastersmith.invitation.cleanup.*}）。
     *
     * @param cron 削除を行う時刻（Spring の cron の形。既定は毎日 3 時 45 分）
     */
    public record Cleanup(@DefaultValue("0 45 3 * * *") String cron) {}
}
