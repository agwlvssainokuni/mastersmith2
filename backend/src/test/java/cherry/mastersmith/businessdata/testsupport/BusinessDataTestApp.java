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
package cherry.mastersmith.businessdata.testsupport;

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.domain.PermissionValues;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.user.service.UserAccountService;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 業務データの結合テストで起動するアプリ（内部DB は組み込みの H2、対象DB は引数で渡す接続先）。利用者・ロール・作業ロールを作り、
 * 業務データの API を送る（unit-test-instructions.md の 7節）。値はすべて架空。
 */
public final class BusinessDataTestApp implements AutoCloseable {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ConfigurableApplicationContext context;

    private final int port;

    private final HttpTestClient client;

    private final RoleActors roleActors;

    private final RoleFixtures fixtures;

    private BusinessDataTestApp(ConfigurableApplicationContext context) {
        this.context = context;
        this.port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
        this.client = new HttpTestClient(port);
        this.roleActors = new RoleActors(
                context.getBean(UserAccountService.class),
                context.getBean(RefreshTokenRevocationService.class),
                context.getBean(PlatformTransactionManager.class),
                port);
        this.fixtures = new RoleFixtures(context.getBean(JdbcTemplate.class));
    }

    /**
     * 対象DB の接続先を設定して起動する。
     *
     * @param dir 内部DB のファイルを置くディレクトリ
     * @param target 接続先
     * @param extra 足す起動の引数（{@code --name=value}）
     * @return 起動したアプリ
     */
    public static BusinessDataTestApp start(Path dir, Target target, String... extra) {
        return start(dir, target, new Class<?>[0], extra);
    }

    /**
     * 対象DB の接続先と、足す部品（テストだけの設定のクラス）を渡して起動する。
     *
     * @param dir 内部DB のファイルを置くディレクトリ
     * @param target 接続先（無ければ null で、対象DB の設定を渡さない）
     * @param sources 足す部品
     * @param extra 足す起動の引数（{@code --name=value}）
     * @return 起動したアプリ
     */
    public static BusinessDataTestApp start(Path dir, Target target, Class<?>[] sources, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.datasource.url=" + TestDatabase.url(dir),
                "--server.port=0",
                "--mastersmith.auth.password.bcrypt-cost=4"));
        if (target != null) {
            args.addAll(List.of(
                    "--mastersmith.target-db.type=" + target.product().name().toLowerCase(Locale.ROOT),
                    "--mastersmith.target-db.host=" + target.host(),
                    "--mastersmith.target-db.port=" + target.port(),
                    "--mastersmith.target-db.database=" + target.database(),
                    "--mastersmith.target-db.schema=" + target.schema(),
                    "--mastersmith.target-db.username=" + target.user(),
                    "--mastersmith.target-db.password=" + target.password()));
        }
        args.addAll(List.of(extra));
        return new BusinessDataTestApp(new SpringApplicationBuilder(MastersmithApplication.class)
                .sources(sources)
                .run(args.toArray(String[]::new)));
    }

    /**
     * 対象DB の接続先。
     *
     * @param product DB の種類
     * @param host ホスト
     * @param port 番号
     * @param database DB の名前
     * @param schema スキーマ
     * @param user ユーザー名
     * @param password パスワード
     */
    public record Target(
            DatabaseProduct product,
            String host,
            int port,
            String database,
            String schema,
            String user,
            String password) {

        /** 接続先と資格情報を出さない。 */
        @Override
        public String toString() {
            return "Target[product=" + product + "]";
        }
    }

    /**
     * 待ち受けの番号を返す。
     *
     * @return 番号
     */
    public int port() {
        return port;
    }

    /**
     * Bean を返す。
     *
     * @param type 型
     * @param <T> 型
     * @return Bean
     */
    public <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    /**
     * Bean を名前で返す。
     *
     * @param name 名前
     * @param type 型
     * @param <T> 型
     * @return Bean
     */
    public <T> T bean(String name, Class<T> type) {
        return context.getBean(name, type);
    }

    /**
     * role の利用者の部品を返す。
     *
     * @return 部品
     */
    public RoleActors roleActors() {
        return roleActors;
    }

    /**
     * role のデータの部品を返す。
     *
     * @return 部品
     */
    public RoleFixtures fixtures() {
        return fixtures;
    }

    /**
     * DSL を適用中にする。
     *
     * @param model モデル（null なら外す）
     */
    public void installDsl(DslModel model) {
        context.getBean(ActiveDslModelHolder.class).replace(model);
    }

    /**
     * ロールを作って設定を置き、ログインした利用者に割り当てて作業ロールにする。
     *
     * @param settings 対象ごとの設定
     * @return 利用者
     */
    public Actor reader(Map<PermissionTarget, PermissionValues> settings) {
        long roleId = role(settings);
        Actor member = roleActors.member();
        fixtures.assignUser(roleId, member.userId());
        fixtures.selectWorkRole(member.userId(), roleId);
        return member;
    }

    /**
     * ロールを作って設定を置く。
     *
     * @param settings 対象ごとの設定
     * @return ロール ID
     */
    public long role(Map<PermissionTarget, PermissionValues> settings) {
        long roleId = fixtures.role(RoleFixtures.uniqueName("業務データ"));
        settings.forEach((target, values) -> fixtures.setting(roleId, target, values));
        return roleId;
    }

    /**
     * 本文を JSON にして POST する。
     *
     * @param path 道
     * @param token アクセストークン（無ければ null）
     * @param body 本文（JSON にする値）
     * @return 応答
     */
    public HttpResponse<String> post(String path, String token, Object body) {
        return postRaw(path, token, MAPPER.writeValueAsString(body));
    }

    /**
     * 本文をそのまま POST する。
     *
     * @param path 道
     * @param token アクセストークン（無ければ null）
     * @param json 本文
     * @return 応答
     */
    public HttpResponse<String> postRaw(String path, String token, String json) {
        HttpRequest.Builder builder = client.request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build());
    }

    @Override
    public void close() {
        context.close();
    }
}
