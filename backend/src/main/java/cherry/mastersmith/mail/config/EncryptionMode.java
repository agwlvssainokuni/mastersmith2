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

import java.util.Properties;

/**
 * SMTP の暗号化の方式（BR1.5、NFR2.6）。Spring Boot の {@code spring.mail.*} の設定から分類する（U1 の項目は持たない。F1 B）。
 *
 * <p>分類の順（security-design.md の 2.3）: (1) {@code protocol} が {@code smtps}、または使う方式の {@code ssl.enable} が true なら
 * SMTPS、(2) {@code mail.smtp.starttls.enable} と {@code mail.smtp.starttls.required} がどちらも true なら STARTTLS（必須）、
 * (3) そのほか（何も無い、{@code starttls.enable} だけの組を含む）は NONE。
 */
public enum EncryptionMode {
    /** 暗号化しない（手元の受け手向け）。 */
    NONE,
    /** STARTTLS を必須にする。受け手が STARTTLS を受け付けないときは平文で続けない。 */
    STARTTLS,
    /** 接続の始めから暗号化する。 */
    SMTPS;

    /** SMTPS の {@code protocol} の値。 */
    static final String PROTOCOL_SMTPS = "smtps";

    /** SMTP の {@code protocol} の値（既定）。 */
    static final String PROTOCOL_SMTP = "smtp";

    /**
     * 設定から方式を分類する。純粋な関数。
     *
     * @param protocol 送信の部品の {@code protocol}（null・空は {@code smtp} とみなす）
     * @param javaMailProperties 送信の部品の Jakarta Mail の設定
     * @return 方式
     */
    public static EncryptionMode classify(String protocol, Properties javaMailProperties) {
        String used = protocol == null || protocol.isEmpty() ? PROTOCOL_SMTP : protocol;
        if (PROTOCOL_SMTPS.equals(used) || isTrue(javaMailProperties, "mail." + used + ".ssl.enable")) {
            return SMTPS;
        }
        if (isTrue(javaMailProperties, "mail.smtp.starttls.enable")
                && isTrue(javaMailProperties, "mail.smtp.starttls.required")) {
            return STARTTLS;
        }
        return NONE;
    }

    /** Jakarta Mail と同じく、値が {@code true}（大文字・小文字は問わない）のときだけ真とする。 */
    private static boolean isTrue(Properties properties, String key) {
        return properties != null && "true".equalsIgnoreCase(properties.getProperty(key));
    }
}
