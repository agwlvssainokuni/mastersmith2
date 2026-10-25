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
import cherry.mastersmith.common.testsupport.JsonLogRecords;
import cherry.mastersmith.common.testsupport.TestDatabase;
import cherry.mastersmith.dsl.service.ActiveDslModelHolder;
import cherry.mastersmith.navigation.testsupport.NavigationActors;
import cherry.mastersmith.navigation.testsupport.NavigationDslFixture;
import cherry.mastersmith.navigation.testsupport.NavigationModels;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import cherry.mastersmith.user.service.UserAccountService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 置き場の問い合わせの値を記録に出さないことの結合テスト（NFR1.8 の部分、BR5.5・BR7.3、{@code observability-design.md} の 2節）。
 *
 * <p>目印の名前を持つテーブルを DSL に置き、置き場の問い合わせを 200・403・400（空の引数）で送る。スパンの属性（テストだけの集め手）・
 * {@code http.server.requests} のタグ・アプリのログ（INFO 以上）・応答の本文（403・400 の {@code instance} は道だけ）に目印が無く、
 * 監査の行が増えないことを確かめる。正しくない {@code %} の並びは受け入れた制約のため対象にしない（{@code observability-design.md} の
 * 3節）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mastersmith.auth.password.bcrypt-cost=4")
@Import({AuthApiTestConfig.class, NavigationQueryExposureIT.SpanCollector.class})
@ExtendWith(OutputCaptureExtension.class)
class NavigationQueryExposureIT {

    /** 目印のスキーマの名前。 */
    static final String SCHEMA_MARK = "zz-marker-schema-7f3a";

    /** 目印のテーブルの名前（見られる）。 */
    static final String VISIBLE_MARK = "zz-marker-visible-91c2";

    /** 目印のテーブルの名前（見られない）。 */
    static final String HIDDEN_MARK = "zz-marker-hidden-4b8e";

    static final List<String> MARKS = List.of(SCHEMA_MARK, VISIBLE_MARK, HIDDEN_MARK);

    /** 終わったスパンの属性をすべて集めるテストだけの部品。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCollector {

        static final List<String> ATTRIBUTES = new CopyOnWriteArrayList<>();

        static final List<String> NAMES = new CopyOnWriteArrayList<>();

        @Bean
        SpanProcessor collectingSpanProcessor() {
            return new SpanProcessor() {
                @Override
                public void onStart(Context parentContext, ReadWriteSpan span) {
                    // 始まりでは集めない。
                }

                @Override
                public boolean isStartRequired() {
                    return false;
                }

                @Override
                public void onEnd(ReadableSpan span) {
                    NAMES.add(span.getName());
                    span.toSpanData()
                            .getAttributes()
                            .forEach((key, value) -> ATTRIBUTES.add(key.getKey() + "=" + value));
                }

                @Override
                public boolean isEndRequired() {
                    return true;
                }

                @Override
                public CompletableResultCode shutdown() {
                    return CompletableResultCode.ofSuccess();
                }
            };
        }
    }

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

    @Autowired
    MeterRegistry meterRegistry;

    private NavigationActors actors;

    private Actor member;

    @BeforeEach
    void setUp() {
        actors = new NavigationActors(
                new RoleActors(userAccountService, revocationService, transactionManager, port),
                new RoleFixtures(jdbc),
                port);
        NavigationDslFixture.install(
                holder,
                List.of(NavigationModels.schema(SCHEMA_MARK, VISIBLE_MARK, HIDDEN_MARK)),
                List.of(NavigationModels.tableItem("目印", SCHEMA_MARK, VISIBLE_MARK)));
        member = actors.memberWithWorkRole(
                actors.role(SCHEMA_MARK, Map.of(VISIBLE_MARK, MainPermission.READ, HIDDEN_MARK, MainPermission.NONE)));
    }

    private int auditRows() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("the names in the query appear in no span attribute, metric tag, log line or error body")
    void namesAreNotRecorded(CapturedOutput output) {
        int before = auditRows();
        SpanCollector.ATTRIBUTES.clear();
        SpanCollector.NAMES.clear();

        // 削除した道（FR3.3）に名前を付けて送っても、名前はどこにも残らない。
        HttpResponse<String> removed =
                actors.get("/api/me/table-access?schema=" + SCHEMA_MARK + "&table=" + HIDDEN_MARK, member.token());
        HttpResponse<String> menu = actors.navigation(member.token());

        assertThat(removed.statusCode()).isEqualTo(404);
        assertThat(menu.statusCode()).isEqualTo(200);
        assertThat(removed.body()).doesNotContain(MARKS);
        assertThat(SpanCollector.NAMES).as("スパンが集まっている（集め手が働いている）").isNotEmpty();
        assertThat(SpanCollector.ATTRIBUTES)
                .as("スパンの属性")
                .allSatisfy(attribute -> assertThat(attribute).doesNotContain(MARKS));
        List<String> tags = meterRegistry.find("http.server.requests").meters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .toList();
        assertThat(tags).allSatisfy(tag -> assertThat(tag).doesNotContain(MARKS));
        List<Map<String, Object>> records = JsonLogRecords.parse(output.getOut()).stream()
                .filter(record -> !"DEBUG".equals(record.get("level")) && !"TRACE".equals(record.get("level")))
                .toList();
        assertThat(records).isNotEmpty();
        assertThat(records).allSatisfy(record -> assertThat(record.toString()).doesNotContain(MARKS));
        assertThat(auditRows()).as("監査の行が増えない").isEqualTo(before);
    }
}
