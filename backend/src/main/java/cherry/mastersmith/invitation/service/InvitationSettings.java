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

import cherry.mastersmith.common.web.MastersmithWebProperties;
import cherry.mastersmith.invitation.domain.BaseUrlRule;
import cherry.mastersmith.invitation.domain.InvitationAvailability;
import cherry.mastersmith.invitation.domain.InvitationToken;
import cherry.mastersmith.invitation.domain.RegistrationUrl;
import cherry.mastersmith.mail.service.MailSender;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 招待の設定の起動時の確かめと、招待を使える設定かの判定（BR1.3〜BR1.6、{@code observability-design.md} 2.2）。判定は起動のときに
 * 1回だけで、動いている間は変わらない。
 *
 * <ul>
 *   <li>有効期限の長さは 1 時間以上で 1 時間で割り切れる長さ、保存の日数は 1 日以上で 1 日で割り切れる長さに限り、合わなければ項目の
 *       名前だけの例外で起動を止める（配備の設定の誤りのため）
 *   <li>ベース URL（既存の {@code mastersmith.web.base-url}）を {@link BaseUrlRule} で判定し、形が合わなければ項目の名前だけの WARN を
 *       1件出す（値は出さない。起動は止めない）
 *   <li>U1 の {@link MailSender#isConfigured()} と合わせて {@link InvitationAvailability} を決め、INFO を1件出す
 * </ul>
 *
 * <p>ベース URL の値は外へ返さず、招待の URL（文字列にすると伏せる型）を組み立てる口だけを持つ（メソッドの呼び出しの追跡が戻り値を
 * 文字列にするため）。
 */
@Component
public class InvitationSettings {

    /** ベース URL の設定の項目の名前（WARN に載せる）。 */
    static final String BASE_URL_ITEM = "mastersmith.web.base-url";

    /** 有効期限の長さの設定の項目の名前。 */
    static final String VALIDITY_ITEM = "mastersmith.invitation.validity";

    /** 保存の日数の設定の項目の名前。 */
    static final String RETENTION_ITEM = "mastersmith.invitation.retention";

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationSettings.class);

    private final Duration validity;

    private final Duration retention;

    private final String baseUrl;

    private final InvitationAvailability availability;

    /**
     * 設定を確かめて作る。
     *
     * @param properties 招待の設定
     * @param webProperties Web の設定（ベース URL）
     * @param mailSender メールの送信（設定の有無だけを読む）
     * @throws IllegalStateException 有効期限の長さ・保存の日数が決まりに合わないとき（起動を止める）
     */
    public InvitationSettings(
            InvitationProperties properties, MastersmithWebProperties webProperties, MailSender mailSender) {
        this.validity = requireWhole(properties.validity(), Duration.ofHours(1), VALIDITY_ITEM);
        this.retention = requireWhole(properties.retention(), Duration.ofDays(1), RETENTION_ITEM);
        BaseUrlRule.Check check = BaseUrlRule.evaluate(webProperties.baseUrl());
        if (check.invalid()) {
            LOGGER.atWarn()
                    .addKeyValue("item", BASE_URL_ITEM)
                    .log("招待のリンクのベースURL の形が正しくないため、招待を使えません。http か https の絶対URL を設定してください");
        }
        this.baseUrl = check.value();
        this.availability = InvitationAvailability.of(baseUrl != null, mailSender.isConfigured());
        LOGGER.atInfo()
                .addKeyValue("enabled", availability.enabled())
                .addKeyValue(
                        "unavailableReasons",
                        availability.unavailableReasons().stream()
                                .map(Enum::name)
                                .toList())
                .log("招待を使える設定かを点検しました");
    }

    /** 長さが正で、単位で割り切れることを確かめる（値は例外のメッセージに載せない）。 */
    private static Duration requireWhole(Duration value, Duration unit, String item) {
        if (value == null || value.compareTo(unit) < 0 || value.toNanos() % unit.toNanos() != 0) {
            throw new IllegalStateException("設定 " + item + " が決まりに合いません（正の値で、単位で割り切れる長さにしてください）");
        }
        return value;
    }

    /**
     * 招待の有効期限の長さを返す。
     *
     * @return 長さ（1 時間の整数倍）
     */
    public Duration validity() {
        return validity;
    }

    /**
     * 招待メールに差し込む有効期限の時間の数を返す（符号と先頭のゼロの無い10進の文字列。BR1.6・BR4.2）。
     *
     * @return 時間の数（既定 {@code "24"}）
     */
    public String validityHours() {
        return Long.toString(validity.toHours());
    }

    /**
     * 終わった招待・期限切れの招待中を残す長さを返す。
     *
     * @return 長さ（1 日の整数倍）
     */
    public Duration retention() {
        return retention;
    }

    /**
     * 招待を使える設定かの判定を返す。
     *
     * @return 判定
     */
    public InvitationAvailability availability() {
        return availability;
    }

    /**
     * 招待の URL をベース URL だけから組み立てる（BR3.4）。
     *
     * @param token 招待のトークン
     * @return 招待の URL
     * @throws IllegalStateException ベース URL に使える値が無いとき（先に {@link #availability()} で確かめる）
     */
    public RegistrationUrl registrationUrl(InvitationToken token) {
        if (baseUrl == null) {
            throw new IllegalStateException("招待のリンクのベースURL がありません");
        }
        return RegistrationUrl.of(baseUrl, token);
    }
}
