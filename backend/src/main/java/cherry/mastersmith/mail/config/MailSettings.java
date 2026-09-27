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

import cherry.mastersmith.mail.domain.MailAddressRule;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 起動時に1回だけ点検した、メールの設定の状態（functional-spec.md の 1節、security-design.md の 2.2）。設定がない・不正・使える
 * のどれかで、動いている間は変わらない。
 *
 * <p>U1 は送信の部品（Spring Boot の自動設定が作る {@link JavaMailSenderImpl}）の設定を読むだけで書き換えない。資格情報の値は
 * 持たず、文字列にするときは状態と方式だけを出す。
 */
public sealed interface MailSettings {

    /** 接続先の項目の名前（WARN に出すのは項目の名前だけ。値は出さない。NFR2.1）。 */
    String HOST = "spring.mail.host";

    /** ポートの項目の名前。 */
    String PORT = "spring.mail.port";

    /** 方式（protocol）の項目の名前。 */
    String PROTOCOL = "spring.mail.protocol";

    /** 資格情報のユーザー名の項目の名前。 */
    String USERNAME = "spring.mail.username";

    /** 資格情報のパスワードの項目の名前。 */
    String PASSWORD = "spring.mail.password";

    /** 差出人の項目の名前。 */
    String FROM = MastersmithMailProperties.PREFIX + ".from";

    /** 差出人の表示名の項目の名前。 */
    String FROM_NAME = MastersmithMailProperties.PREFIX + ".from-name";

    /** 表示名の既定（BR1.6）。 */
    String DEFAULT_FROM_NAME = "MasterSmith";

    /** 時間切れの3つの鍵（{@code mail.<protocol>.} の後ろ）。 */
    List<String> TIMEOUT_KEYS = List.of("connectiontimeout", "timeout", "writetimeout");

    /** 1 以上の整数の形（先頭の 0 を含めてよい）。 */
    Pattern POSITIVE_INTEGER = Pattern.compile("[0-9]{1,10}");

    /** SMTP の項目が無く、送らない（WARN は出さない。BR1.2）。 */
    record NotConfigured() implements MailSettings {}

    /**
     * 設定に欠け・不正があるため送らない（BR1.3・BR1.4、NFR2.5・NFR2.6・NFR6.2）。
     *
     * @param problemItems 問題のある項目の名前（値は含めない）
     */
    record Invalid(List<String> problemItems) implements MailSettings {

        /** 一覧を変更できないものにする。 */
        public Invalid {
            problemItems = List.copyOf(problemItems);
        }
    }

    /**
     * 使える。SMTP にはまだ接続していない（functional-spec.md の 1節の手順 5）。
     *
     * @param sender 送信の部品（設定は読むだけ）
     * @param from 表示名つきの差出人
     * @param mode 暗号化の方式
     */
    record Usable(JavaMailSenderImpl sender, InternetAddress from, EncryptionMode mode) implements MailSettings {

        /** 必須の値を確かめる。 */
        public Usable {
            Objects.requireNonNull(sender, "sender は必須です");
            Objects.requireNonNull(from, "from は必須です");
            Objects.requireNonNull(mode, "mode は必須です");
        }

        /** 送信の部品と差出人を伏せ、状態と方式だけを文字列にする（部品の文字列化に頼らない）。 */
        @Override
        public String toString() {
            return "Usable[mode=" + mode + "]";
        }
    }

    /**
     * 設定を点検する（security-design.md の 2.2 の表）。問題をすべて集め、1つでもあれば {@link Invalid} にする。ログは出さない
     * （WARN は呼び出し側が1回だけ出す）。
     *
     * @param senderOrNull 送信の部品（接続先の設定が無く、自動設定が作らなかったときは null）
     * @param properties 差出人の設定
     * @return 点検の結果
     */
    static MailSettings inspect(JavaMailSenderImpl senderOrNull, MastersmithMailProperties properties) {
        String from = properties == null ? null : properties.from();
        String fromName = properties == null ? null : properties.fromName();
        if (senderOrNull == null && isAbsent(from)) {
            return new NotConfigured();
        }
        List<String> problems = new ArrayList<>();
        if (senderOrNull == null || isAbsent(senderOrNull.getHost())) {
            problems.add(HOST);
        }
        if (isAbsent(from) || containsLineBreak(from) || !MailAddressRule.isValid(from)) {
            problems.add(FROM);
        }
        if (containsLineBreak(fromName)) {
            problems.add(FROM_NAME);
        }
        EncryptionMode mode = EncryptionMode.NONE;
        if (senderOrNull != null) {
            mode = EncryptionMode.classify(senderOrNull.getProtocol(), senderOrNull.getJavaMailProperties());
            addSenderProblems(senderOrNull, mode, problems);
        }
        if (!problems.isEmpty()) {
            return new Invalid(problems);
        }
        return new Usable(senderOrNull, fromAddress(from, fromName), mode);
    }

    /** 送信の部品の設定（資格情報・ポート・方式・時間切れ）を点検する（2.2 の表の 5〜9）。 */
    private static void addSenderProblems(JavaMailSenderImpl sender, EncryptionMode mode, List<String> problems) {
        boolean hasUsername = !isAbsent(sender.getUsername());
        boolean hasPassword = !isAbsent(sender.getPassword());
        if (hasUsername && !hasPassword) {
            problems.add(PASSWORD);
        }
        if (hasPassword && !hasUsername) {
            problems.add(USERNAME);
        }
        int port = sender.getPort();
        if (port != JavaMailSenderImpl.DEFAULT_PORT && (port < 1 || port > 65535)) {
            problems.add(PORT);
        }
        // protocol が無い（null）ときは、送信の部品と同じく smtp とみなす（空の文字列は不正）。
        String protocol = sender.getProtocol() == null ? EncryptionMode.PROTOCOL_SMTP : sender.getProtocol();
        boolean knownProtocol =
                EncryptionMode.PROTOCOL_SMTP.equals(protocol) || EncryptionMode.PROTOCOL_SMTPS.equals(protocol);
        if (!knownProtocol) {
            problems.add(PROTOCOL);
        }
        String prefix = EncryptionMode.PROTOCOL_SMTPS.equals(protocol) ? "mail.smtps." : "mail.smtp.";
        Properties javaMailProperties = sender.getJavaMailProperties();
        for (String key : TIMEOUT_KEYS) {
            if (!isPositiveInteger(javaMailProperties.getProperty(prefix + key))) {
                problems.add("spring.mail.properties." + prefix + key);
            }
        }
        if (hasUsername && hasPassword && mode == EncryptionMode.NONE) {
            problems.add(USERNAME);
        }
    }

    /** 表示名つきの差出人を組み立てる（表示名は UTF-8 で規格どおりに符号化される。BR1.6・BR5.1）。 */
    private static InternetAddress fromAddress(String from, String fromName) {
        String personal = isAbsent(fromName) ? DEFAULT_FROM_NAME : fromName;
        try {
            return new InternetAddress(from, personal, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 は必ず使えるため起きない。
            throw new IllegalStateException("UTF-8 を使えません", e);
        }
    }

    /** Jakarta Mail が読める 1 以上の整数（int の範囲）かを返す。 */
    private static boolean isPositiveInteger(String value) {
        if (value == null || !POSITIVE_INTEGER.matcher(value).matches()) {
            return false;
        }
        long number = Long.parseLong(value);
        return number >= 1 && number <= Integer.MAX_VALUE;
    }

    private static boolean isAbsent(String value) {
        return value == null || value.isBlank();
    }

    private static boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }
}
