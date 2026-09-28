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

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 招待の結合テストで、設定を変えてアプリを起動する補助（受け手の番号・ベース URL・有効期限の長さをテストごとに変えるとき）。本番の
 * テンプレートのまま起動する。
 */
public final class InvitationApps {

    private InvitationApps() {}

    /**
     * アプリを起動する。内部DB は一時ディレクトリの組み込みの H2、差出人・時間切れ（1 秒）・bcrypt の cost 4、定期の削除は止める。
     *
     * @param dir 内部DB の置き場
     * @param settings 追加・上書きする設定（{@code spring.mail.host}・{@code spring.mail.port}・{@code mastersmith.web.base-url} など）
     * @return 起動したアプリ
     */
    public static ConfigurableApplicationContext start(Path dir, Map<String, String> settings) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("spring.datasource.url", TestDatabase.url(dir));
        all.put("server.port", "0");
        all.put("mastersmith.auth.password.bcrypt-cost", "4");
        all.put("mastersmith.mail.from", InvitationTestProperties.FROM);
        all.put("mastersmith.invitation.cleanup.cron", "-");
        for (String protocol : List.of("smtp", "smtps")) {
            for (String key : List.of("connectiontimeout", "timeout", "writetimeout")) {
                all.put("spring.mail.properties.mail." + protocol + "." + key, InvitationTestProperties.TIMEOUT_MILLIS);
            }
        }
        all.putAll(settings);
        List<String> args = new ArrayList<>();
        all.forEach((key, value) -> args.add("--" + key + "=" + value));
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new));
    }

    /**
     * SMTP の受け手の設定を返す。
     *
     * @param port 受け手の番号
     * @param baseUrl ベース URL（null なら設定しない）
     * @return 設定
     */
    public static Map<String, String> mail(int port, String baseUrl) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("spring.mail.host", "localhost");
        settings.put("spring.mail.port", Integer.toString(port));
        if (baseUrl != null) {
            settings.put("mastersmith.web.base-url", baseUrl);
        }
        return settings;
    }

    /**
     * 起動したアプリの待ち受けの番号を返す。
     *
     * @param context アプリ
     * @return 番号
     */
    public static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    /**
     * 起動したアプリで管理者を作ってログインし、アクセストークンを返す。
     *
     * @param context アプリ
     * @return アクセストークン
     */
    public static String adminToken(ConfigurableApplicationContext context) {
        AdminTestUsers users = new AdminTestUsers(context.getBean(UserAccountService.class), port(context));
        return users.accessToken(users.createAdmin());
    }
}
