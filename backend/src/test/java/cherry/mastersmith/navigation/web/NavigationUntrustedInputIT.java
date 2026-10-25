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
package cherry.mastersmith.navigation.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.auth.service.RefreshTokenRevocationService;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.DslMenuItem;
import cherry.mastersmith.dsl.domain.DslSchema;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.dsl.domain.TableRef;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleDslFixture;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 信頼できない入力の結合テスト（BR3.2・BR4.2・BR5.1、NFR1.7、AC5.1.7・AC5.1.13、計画の 13節 Q3: A・Q4: A・D-9）。
 *
 * <p>名前は捨ての試し N1 の 23 種（DSL の検証が拒みうる制御文字を含む）。モデルは {@link NavigationDslFixture} で直に置く。スキーマの
 * 名前にも記号を入れる。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import(AuthApiTestConfig.class)
class NavigationUntrustedInputIT {

    /** 記号を含むスキーマの名前。 */
    static final String SCHEMA = "s/..;%?# x";

    /** 捨ての試し N1 の 23 種の名前。 */
    static final List<String> NAMES = List.of(
            "/",
            "..",
            "../x",
            "?",
            "#",
            "%",
            "%25",
            "a b",
            ";",
            "javascript:alert(1)",
            "https://example.com/x",
            "\\",
            "a&b",
            "a=b",
            "<'\"&>",
            "受注明細",
            "😀",
            "x".repeat(300),
            "a\tb",
            "a\nb",
            "a\rb",
            "a\u0000b",
            "a\u007Fb");

    /** 空白だけの名前（空と見なさない。計画の 13節 Q4: A）。 */
    static final String BLANK = " ";

    /** 表示名に入れる記号。 */
    static final String MARKUP = "<script>alert(1)</script><>&\"'";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, tempDir);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserAccountService userAccountService;

    @Autowired
    RefreshTokenRevocationService revocationService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ActiveDslModelHolder holder;

    @Autowired
    JdbcTemplate jdbc;

    private NavigationActors actors;

    private Actor reader;

    @BeforeEach
    void setUp() {
        RoleActors roleActors = new RoleActors(userAccountService, revocationService, transactionManager, port);
        actors = new NavigationActors(roleActors, new RoleFixtures(jdbc), port);
        List<String> tables = new ArrayList<>(NAMES);
        tables.add(BLANK);
        Map<String, DslTable> map = new LinkedHashMap<>();
        for (String name : tables) {
            map.put(name, RoleDslFixture.table(name, "ID"));
        }
        map.put(
                "LABELED",
                new DslTable(
                        "LABELED",
                        new DisplayName(MARKUP, MARKUP),
                        false,
                        List.of("ID"),
                        List.of(),
                        Map.of("ID", RoleDslFixture.column("ID"))));
        DslSchema schema = new DslSchema(SCHEMA, new DisplayName("記号", "Symbols"), map);
        NavigationDslFixture.install(holder, List.of(schema), menus());
        // 名前の列の長さ（256）を超える 300 文字のテーブルにも当たるよう、スキーマの階層の明示の値を継承させる。
        reader = actors.memberWithWorkRole(actors.schemaRole(SCHEMA, MainPermission.READ));
    }

    private static List<DslMenuItem> menus() {
        List<DslMenuItem> menus = new ArrayList<>();
        menus.add(new DslMenuItem(new DisplayName(MARKUP, ""), "user", new TableRef(SCHEMA, "LABELED"), List.of()));
        for (String icon : new String[] {"User", "", " user", "user ", "unknown", "<svg>"}) {
            menus.add(new DslMenuItem(new DisplayName("i", "i"), icon, new TableRef(SCHEMA, "LABELED"), List.of()));
        }
        menus.add(new DslMenuItem(new DisplayName("n", "n"), null, new TableRef(SCHEMA, "LABELED"), List.of()));
        for (String name : NAMES) {
            menus.add(new DslMenuItem(new DisplayName(name, name), null, new TableRef(SCHEMA, name), List.of()));
        }
        return menus;
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "labels with markup are returned unchanged and icons are checked against the list")
    void labelsAndIcons() {
        Map<String, Object> body = HttpTestClient.json(actors.navigation(reader.token()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        assertThat(items.getFirst())
                .containsEntry("label", Map.of("ja", MARKUP, "en", ""))
                .containsEntry("icon", "user");
        assertThat(items.subList(1, 8)).extracting(item -> item.get("icon")).containsOnly("list");
        assertThat(items.subList(8, items.size()))
                .extracting(item -> (Object) ((Map<?, ?>) item.get("table")).get("tableName"))
                .containsExactlyElementsOf(List.<Object>copyOf(NAMES));
    }
}
