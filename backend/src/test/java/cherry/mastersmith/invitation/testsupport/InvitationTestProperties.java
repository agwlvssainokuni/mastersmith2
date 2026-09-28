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
package cherry.mastersmith.invitation.testsupport;

import cherry.mastersmith.common.testsupport.TestDatabase;
import java.nio.file.Path;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 招待の結合テストで、アプリを本番のテンプレートのまま、内部DB・SMTP の受け手の番号・差出人・ベース URL・短い時間切れで起動する
 * 設定（{@code unit-test-instructions.md} 1節）。U1 の {@code MailTestApplication} はテスト用のテンプレートに差し替えるため使わない。
 *
 * <p>定期の削除は止め（{@code -}）、テストの中から直接呼ぶ。
 */
public final class InvitationTestProperties {

    /** テストの差出人。 */
    public static final String FROM = "noreply@example.com";

    /** 送信の時間切れ（ミリ秒）。 */
    public static final String TIMEOUT_MILLIS = "1000";

    private InvitationTestProperties() {}

    /**
     * 設定を登録する。
     *
     * @param registry Spring のテストの設定の登録先
     * @param dir 内部DB の置き場
     * @param smtpPort SMTP の受け手の番号
     * @param baseUrl 招待のリンクのベース URL（null なら設定しない）
     */
    public static void register(DynamicPropertyRegistry registry, Path dir, int smtpPort, String baseUrl) {
        TestDatabase.register(registry, dir);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> Integer.toString(smtpPort));
        registry.add("mastersmith.mail.from", () -> FROM);
        for (String protocol : List.of("smtp", "smtps")) {
            for (String key : List.of("connectiontimeout", "timeout", "writetimeout")) {
                registry.add("spring.mail.properties.mail." + protocol + "." + key, () -> TIMEOUT_MILLIS);
            }
        }
        if (baseUrl != null) {
            registry.add("mastersmith.web.base-url", () -> baseUrl);
        }
        registry.add("mastersmith.invitation.cleanup.cron", () -> "-");
        registry.add("mastersmith.auth.password.bcrypt-cost", () -> "4");
    }
}
