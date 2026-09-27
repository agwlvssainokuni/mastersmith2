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
package cherry.mastersmith.mail.config;

import cherry.mastersmith.mail.template.MailTemplateCatalog;
import cherry.mastersmith.mail.template.MailTemplateRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * メールの設定の点検（BR1.1〜BR1.7、NFR2.5・NFR2.6・NFR6.2、security-design.md の 2節）と、テンプレートの準備
 * （BR2.1〜BR2.3）。
 *
 * <p>送信の部品（Spring Boot のメールの自動設定が作る {@link JavaMailSenderImpl}）は、接続先（{@code spring.mail.host}）が
 * あるときだけ作られるため「あれば使う」形で受ける。U1 は部品の設定を読むだけで書き換えない。
 */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailConfig.class);

    /**
     * 起動時に1回だけ設定を点検する。欠け・不正があれば、項目の名前だけを WARN で1件出す（値は出さない。BR1.3、NFR2.1）。
     * あわせて、設定の状態（設定がない・不正・ある）と方式だけを INFO で1行出す（接続先・差出人・資格情報の値は出さない。
     * 計画の 8節の P2）。
     *
     * @param senderProvider 送信の部品（無ければ空）
     * @param properties 差出人の設定
     * @return 点検の結果
     */
    @Bean
    public MailSettings mailSettings(
            ObjectProvider<JavaMailSenderImpl> senderProvider, MastersmithMailProperties properties) {
        MailSettings settings = MailSettings.inspect(senderProvider.getIfUnique(), properties);
        if (settings instanceof MailSettings.Invalid invalid) {
            LOGGER.atWarn()
                    .addKeyValue("items", String.join(", ", invalid.problemItems()))
                    .log("メールの設定に欠け・不正があるため、メールを送りません。項目を直して再起動してください");
        }
        LOGGER.atInfo()
                .addKeyValue("state", stateName(settings))
                .addKeyValue("encryption", encryptionName(settings))
                .log("メールの設定を点検しました");
        return settings;
    }

    /**
     * 起動のときに本番の一覧と置き場のテンプレートを準備する。欠け・壊れ・名前の誤りがあれば起動を止める（BR2.3）。準備した
     * テンプレートの件数と名前（templateId と language）だけを INFO で1行出す（計画の 8節の P2）。
     *
     * @param resolver リソースの数え上げと読み込み（アプリの文脈）
     * @return 準備したテンプレート
     */
    @Bean
    public MailTemplateRegistry mailTemplateRegistry(ResourcePatternResolver resolver) {
        MailTemplateRegistry registry =
                MailTemplateRegistry.prepare(MailTemplateCatalog.DEFINITIONS, MailTemplateCatalog.LOCATION, resolver);
        LOGGER.atInfo()
                .addKeyValue("count", registry.preparedTemplateNames().size())
                .addKeyValue("templates", String.join(", ", registry.preparedTemplateNames()))
                .log("メールのテンプレートを準備しました");
        return registry;
    }

    /**
     * 設定の状態の名前を返す（ログに出す値。設定の値は含めない）。
     *
     * @param settings 点検の結果
     * @return {@code NOT_CONFIGURED}・{@code INVALID}・{@code CONFIGURED} のどれか
     */
    static String stateName(MailSettings settings) {
        return switch (settings) {
            case MailSettings.NotConfigured notConfigured -> "NOT_CONFIGURED";
            case MailSettings.Invalid invalid -> "INVALID";
            case MailSettings.Usable usable -> "CONFIGURED";
        };
    }

    /**
     * 使う暗号化の方式の名前を返す。使えない設定では {@code UNUSED}（送らないため）。
     *
     * @param settings 点検の結果
     * @return 方式の名前
     */
    static String encryptionName(MailSettings settings) {
        return settings instanceof MailSettings.Usable usable ? usable.mode().name() : "UNUSED";
    }
}
