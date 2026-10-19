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
package cherry.mastersmith.businessdata.web;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 業務データの読み取りのトレースと指標に、行の値・スキーマ名・テーブル名が残らないことの結合テスト（BR10.4、NFR4.2、
 * observability-design.md、既存の {@code NavigationQueryExposureIT} と同じ形）。PostgreSQL のコンテナ。
 *
 * <p>終わったスパンの属性をテストだけの集め手で集め、{@code http.server.requests} のタグとあわせて、道が3つの固定の道だけで、名前と
 * 値が無いことを確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataTraceExposureIT {

    /** 検索の値の目印。 */
    private static final String SEARCH_MARK = "zz-trace-search-3c9d";

    /** 主キーの値の目印。 */
    private static final String KEY_MARK = "87654329";

    /** 終わったスパンの属性をすべて集めるテストだけの部品。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCollector {

        static final List<String> ATTRIBUTES = new CopyOnWriteArrayList<>();

        static final List<String> NAMES = new CopyOnWriteArrayList<>();

        @Bean
        SpanProcessor businessDataCollectingSpanProcessor() {
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

    private final BusinessDataSampleSchema sample = new BusinessDataSampleSchema(DatabaseProduct.POSTGRESQL);

    private BusinessDataTestApp app;

    private Actor member;

    @BeforeAll
    void start(@TempDir Path dir) {
        sample.start();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), new Class<?>[] {SpanCollector.class});
        app.installDsl(sample.model());
        member = app.reader(sample.settings(MainPermission.READ));
    }

    @AfterAll
    void stop() {
        try {
            if (app != null) {
                app.close();
            }
        } finally {
            sample.stop();
        }
    }

    @Test
    @DisplayName("span attributes and metric tags carry only the three fixed paths and no name or value")
    void noNamesOrValuesInTraces() {
        SpanCollector.ATTRIBUTES.clear();
        SpanCollector.NAMES.clear();

        HttpResponse<String> definition =
                app.post(BusinessDataPaths.DEFINITION, member.token(), sample.body(BusinessDataSampleSchema.ITEM));
        HttpResponse<String> search = app.post(
                BusinessDataPaths.SEARCH,
                member.token(),
                sample.body(
                        BusinessDataSampleSchema.ITEM,
                        "conditions",
                        List.of(Map.of("column", "name", "value", SEARCH_MARK))));
        HttpResponse<String> detail = app.post(
                BusinessDataPaths.GET,
                member.token(),
                sample.body(BusinessDataSampleSchema.ITEM, "key", Map.of("id", "1")));
        HttpResponse<String> missing = app.post(
                BusinessDataPaths.GET,
                member.token(),
                sample.body(BusinessDataSampleSchema.ITEM, "key", Map.of("id", KEY_MARK)));

        assertThat(List.of(definition, search, detail, missing))
                .extracting(HttpResponse::statusCode)
                .containsExactly(200, 200, 200, 404);
        List<String> marks =
                List.of(SEARCH_MARK, KEY_MARK, BusinessDataSampleSchema.SECRET_NAME, sample.schema(), sample.reader());
        assertThat(SpanCollector.NAMES).as("スパンが集まっている（集め手が働いている）").isNotEmpty();
        assertThat(SpanCollector.ATTRIBUTES)
                .allSatisfy(attribute -> assertThat(attribute).doesNotContain(marks));
        List<String> uris = SpanCollector.ATTRIBUTES.stream()
                .filter(attribute -> attribute.startsWith("http.route=") || attribute.startsWith("uri="))
                .filter(attribute -> attribute.contains("/api/tables"))
                .distinct()
                .toList();
        assertThat(uris)
                .allSatisfy(uri -> assertThat(List.of(
                                "=" + BusinessDataPaths.DEFINITION,
                                "=" + BusinessDataPaths.SEARCH,
                                "=" + BusinessDataPaths.GET))
                        .anyMatch(uri::endsWith));
        List<String> tags = app.bean(MeterRegistry.class).find("http.server.requests").meters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .toList();
        assertThat(tags).contains("uri=" + BusinessDataPaths.SEARCH, "uri=" + BusinessDataPaths.GET);
        assertThat(tags).allSatisfy(tag -> assertThat(tag).doesNotContain(marks));
    }
}
