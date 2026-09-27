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
package cherry.mastersmith.mail.testsupport;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.mail.template.MailTemplateRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * メールの結合テストで、アプリ全体を起動する補助。テンプレートはテスト用の一覧と置き場に置き換える（B1 の本番の一覧は空のため）。
 *
 * <p>置き換えの設定は {@code mastersmith.test-fixture.mail-test-templates=true} を渡したテストだけで有効になり、ほかのテストの
 * 起動には入らない。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(MailTestApplication.PROPERTY)
public class MailTestApplication {

    /** テスト用のテンプレートに置き換えるかどうかの設定の名前。 */
    public static final String PROPERTY = "mastersmith.test-fixture.mail-test-templates";

    /** テストの差出人。 */
    public static final String FROM = "noreply@example.com";

    /**
     * テスト用の一覧と置き場で準備したテンプレート（本番のものより優先する）。
     *
     * @return 準備したテンプレート
     */
    @Bean
    @Primary
    public MailTemplateRegistry testMailTemplateRegistry() {
        return MailTestTemplates.registry();
    }

    /**
     * アプリを起動する。内部DB は一時ディレクトリの組み込みの H2、メールの接続先は {@code 127.0.0.1} の指定した番号、時間切れは
     * 短い値（テストの設定の既定 1 秒）にする。
     *
     * @param dir 内部DB の置き場
     * @param port SMTP の受け手の番号
     * @param settings 追加・上書きする設定
     * @return 起動したアプリ
     */
    public static ConfigurableApplicationContext start(Path dir, int port, Map<String, String> settings) {
        Map<String, String> all = new java.util.LinkedHashMap<>();
        all.put("spring.datasource.url", TestDatabase.url(dir));
        all.put("server.port", "0");
        all.put("mastersmith.auth.password.bcrypt-cost", "4");
        all.put(PROPERTY, "true");
        all.put("spring.mail.host", "localhost");
        all.put("spring.mail.port", Integer.toString(port));
        all.put("mastersmith.mail.from", FROM);
        for (String protocol : List.of("smtp", "smtps")) {
            for (String key : List.of("connectiontimeout", "timeout", "writetimeout")) {
                all.put("spring.mail.properties.mail." + protocol + "." + key, "1000");
            }
        }
        all.putAll(settings);
        List<String> args = new ArrayList<>();
        all.forEach((key, value) -> args.add("--" + key + "=" + value));
        return new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new));
    }

    /**
     * 閉じた（何も待ち受けていない）手元の番号を返す。
     *
     * @return 番号
     * @throws IOException 番号を得られないとき
     */
    public static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
