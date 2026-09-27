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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * メールの差出人の設定（{@code mastersmith.mail.*}、U1）。SMTP の接続先・ポート・資格情報・暗号化・時間切れは Spring Boot の
 * {@code spring.mail.*} で受け取り、ここでは差出人と表示名だけを受ける（Spring Boot の {@code MailProperties} と名前を分ける）。
 *
 * <p>文字列で受け、{@code @Validated} を付けない。値が不正でも起動を続け、起動時の点検（{@link MailSettings#inspect}）で
 * 「設定がない」にするため（BR1.3）。設定は環境変数だけから受け取る（BR1.1）。
 *
 * @param from 差出人のメールアドレス（文字列にするときは有無だけ）
 * @param fromName 差出人の表示名（無ければ {@code MasterSmith}。文字列にするときは有無だけ）
 */
@ConfigurationProperties("mastersmith.mail")
public record MastersmithMailProperties(String from, String fromName) {

    /** 設定の接頭辞。 */
    public static final String PREFIX = "mastersmith.mail";

    /** 差出人と表示名の値を伏せて、有無だけを文字列にする（security-design.md の 4.4）。 */
    @Override
    public String toString() {
        return "MastersmithMailProperties[from=" + presence(from) + ", fromName=" + presence(fromName) + "]";
    }

    private static String presence(String value) {
        return value == null || value.isEmpty() ? "(empty)" : "***";
    }
}
