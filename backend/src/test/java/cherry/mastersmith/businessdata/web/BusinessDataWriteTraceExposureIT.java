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
 * 業務データの書き込みのトレースと指標に、行の値・主キーの値・スキーマ名・テーブル名が残らないことの結合テスト（Intent
 * 261016-business-data-views の U2、計画の Step 23、BR12.3、NFR4.2、U1 の {@code BusinessDataTraceExposureIT} と同じ形）。PostgreSQL
 * のコンテナ。終わったスパンの属性をテストだけの集め手で集め（外へ送る直前と同じ属性）、{@code http.server.requests} のタグとあわせて、
 * 道が書き込みの2つの固定の道だけで、名前と値が無いことを確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class BusinessDataWriteTraceExposureIT {

    /** 値の目印。 */
    private static final String VALUE_MARK = "zz-trace-value-6a1b";

    /** 主キーの値の目印。 */
    private static final String KEY_MARK = "86420975";

    /** 終わったスパンの属性をすべて集めるテストだけの部品。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCollector {

        static final List<String> ATTRIBUTES = new CopyOnWriteArrayList<>();

        static final List<String> NAMES = new CopyOnWriteArrayList<>();

        @Bean
        SpanProcessor businessDataWriteCollectingSpanProcessor() {
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
        app = BusinessDataTestApp.start(
                dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[] {SpanCollector.class});
        app.installDsl(sample.model());
        member = app.reader(sample.writeSettings());
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

    private Map<String, Object> body() {
        Map<String, Object> body = sample.body(BusinessDataSampleSchema.ITEM);
        body.put("definitionId", app.definitionId(member.token(), sample.schema(), BusinessDataSampleSchema.ITEM));
        return body;
    }

    @Test
    @DisplayName("span attributes and metric tags carry only the fixed write paths and no name, key or value")
    void noNamesOrValuesInTraces() {
        SpanCollector.ATTRIBUTES.clear();
        SpanCollector.NAMES.clear();

        Map<String, Object> create = body();
        create.put("values", Map.of("id", KEY_MARK, "name", VALUE_MARK));
        HttpResponse<String> created = app.post(BusinessDataPaths.ROWS, member.token(), create);
        Map<String, Object> update = body();
        update.put("key", Map.of("id", KEY_MARK));
        update.put("values", Map.of("name", VALUE_MARK + "-u"));
        HttpResponse<String> updated = app.patch(BusinessDataPaths.ROWS, member.token(), update);
        HttpResponse<String> duplicate = app.post(BusinessDataPaths.ROWS, member.token(), create);
        Map<String, Object> delete = body();
        delete.put("key", Map.of("id", KEY_MARK));
        HttpResponse<String> deleted = app.post(BusinessDataPaths.DELETE, member.token(), delete);

        assertThat(List.of(created, updated, duplicate, deleted))
                .extracting(HttpResponse::statusCode)
                .containsExactly(201, 200, 409, 200);
        List<String> marks =
                List.of(VALUE_MARK, KEY_MARK, sample.schema(), sample.writer().user());
        assertThat(SpanCollector.NAMES).as("スパンが集まっている（集め手が働いている）").isNotEmpty();
        assertThat(SpanCollector.ATTRIBUTES)
                .allSatisfy(attribute -> assertThat(attribute).doesNotContain(marks));
        List<String> uris = SpanCollector.ATTRIBUTES.stream()
                .filter(attribute -> attribute.startsWith("http.route=") || attribute.startsWith("uri="))
                .filter(attribute -> attribute.contains("/api/tables/rows"))
                .distinct()
                .toList();
        assertThat(uris).isNotEmpty();
        assertThat(uris)
                .allSatisfy(uri -> assertThat(List.of("=" + BusinessDataPaths.ROWS, "=" + BusinessDataPaths.DELETE))
                        .anyMatch(uri::endsWith));
        List<String> tags = app.bean(MeterRegistry.class).find("http.server.requests").meters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .toList();
        assertThat(tags).contains("uri=" + BusinessDataPaths.ROWS, "uri=" + BusinessDataPaths.DELETE);
        assertThat(tags).allSatisfy(tag -> assertThat(tag).doesNotContain(marks));
    }
}
