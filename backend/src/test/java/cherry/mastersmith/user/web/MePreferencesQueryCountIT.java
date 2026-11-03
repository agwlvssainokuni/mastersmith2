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
package cherry.mastersmith.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.access.testsupport.AdminTestUsers;
import cherry.mastersmith.auth.testsupport.AuthApiTestConfig;
import cherry.mastersmith.auth.testsupport.SqlStatementCounter;
import cherry.mastersmith.businessdata.testsupport.CountingDataSource;
import cherry.mastersmith.businessdata.testsupport.CountingDataSourceConfig;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.user.service.UserAccountService;
import cherry.mastersmith.user.testsupport.MeApi;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * プリファレンスの取得・保存が内部DB に出す文の数と、借りる接続の数の結合テスト（Intent 261102-timezone-ui-polish の U2 の計画の
 * D-P6、NFR2.1.2）。
 *
 * <p>基準の数は、表示のタイムゾーンを足す前の実装（4 項目の保存）で測って定数にした。足した後の実装も、この数を超えない・内部DB の
 * 接続は 1 本のままであることを同じテストで確かめる。認証と認可の入口の文も同じ要求の中で数える。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "cherry.mastersmith.auth.testsupport.SqlStatementCounter")
@Import({AuthApiTestConfig.class, CountingDataSourceConfig.class})
class MePreferencesQueryCountIT {

    /** 変更の前の実装で測った、GET の文の数。 */
    private static final int BASELINE_GET_STATEMENTS = 2;

    /** 変更の前の実装で測った、PUT の文の数。 */
    private static final int BASELINE_PUT_STATEMENTS = 2;

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
    DataSource dataSource;

    private MeApi me;

    private String token;

    @BeforeEach
    void setUp() {
        me = new MeApi(port);
        AdminTestUsers users = new AdminTestUsers(userAccountService, port);
        token = users.accessToken(users.createNonAdmin());
    }

    private record Measured(int statements, int peakConnections, List<String> kinds) {}

    private Measured measure(Supplier<HttpResponse<String>> request) {
        CountingDataSource counting = (CountingDataSource) dataSource;
        counting.reset();
        SqlStatementCounter.start();
        HttpResponse<String> response = request.get();
        SqlStatementCounter.stop();
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> kinds = SqlStatementCounter.recorded().stream()
                .map(recorded -> SqlStatementCounter.kind(recorded.sql()))
                .toList();
        return new Measured(kinds.size(), counting.peak(), kinds);
    }

    @Test
    @DisplayName("GET does not exceed the baseline statements and borrows one internal connection at a time")
    void getStatements() {
        Measured measured = measure(() -> me.getPreferences(token));

        assertThat(measured.statements()).as(measured.kinds().toString()).isLessThanOrEqualTo(BASELINE_GET_STATEMENTS);
        assertThat(measured.peakConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT of the four fields does not exceed the baseline statements and borrows one internal connection")
    void putStatements() {
        Measured measured = measure(() -> me.putPreferences(token, "数え 太郎", "ja", "light", "md"));

        assertThat(measured.statements()).as(measured.kinds().toString()).isLessThanOrEqualTo(BASELINE_PUT_STATEMENTS);
        assertThat(measured.peakConnections()).isEqualTo(1);
    }
}
