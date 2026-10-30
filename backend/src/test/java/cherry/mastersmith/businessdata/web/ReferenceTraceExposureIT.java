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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.CELL_MARK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.LABEL_MARK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SEARCH_MARK;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.SRC_API;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.TGT;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.VALUE_MARK;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
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
 * 参照先の検索とラベルの1件読みのトレースと指標に、参照先の値・ラベル・検索の値・名前が残らないことの結合テスト（Intent
 * 261029-reference-lookup-cleanup の U1、Step 21、BR10.3、NFR1.5、observability-design.md 3節。既存の
 * {@code BusinessDataTraceExposureIT} と同じ形）。PostgreSQL のコンテナ。終わったスパンの属性をテストだけの集め手で集め、
 * {@code http.server.requests} のタグとあわせて、道が固定の道だけで値が無いことを確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceTraceExposureIT {

    /** 終わったスパンの属性をすべて集めるテストだけの部品。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCollector {

        static final List<String> ATTRIBUTES = new CopyOnWriteArrayList<>();

        static final List<String> NAMES = new CopyOnWriteArrayList<>();

        @Bean
        SpanProcessor referenceCollectingSpanProcessor() {
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
        ReferenceTables tables = new ReferenceTables(sample);
        tables.createApi();
        app = BusinessDataTestApp.start(dir.resolve("main"), sample.target(), new Class<?>[] {SpanCollector.class});
        app.installDsl(sample.model(tables.apiTables()));
        member = app.reader(tables.apiSettings(Map.of()));
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
    @DisplayName("span attributes and metric tags carry only the fixed path and no value, label or name")
    void noValuesInTraces() {
        SpanCollector.ATTRIBUTES.clear();
        SpanCollector.NAMES.clear();

        HttpResponse<String> search = app.post(
                BusinessDataPaths.REFERENCE_ROWS,
                member.token(),
                sample.body(
                        SRC_API,
                        "column",
                        "pick",
                        "conditions",
                        List.of(Map.of("column", "s", "value", SEARCH_MARK)),
                        "page",
                        1));
        HttpResponse<String> label = app.post(
                BusinessDataPaths.REFERENCE_ROWS,
                member.token(),
                sample.body(SRC_API, "column", "ref", "value", VALUE_MARK));
        HttpResponse<String> missing = app.post(
                BusinessDataPaths.REFERENCE_ROWS,
                member.token(),
                sample.body(SRC_API, "column", "ref", "value", VALUE_MARK + "-missing"));

        assertThat(List.of(search, label, missing))
                .extracting(HttpResponse::statusCode)
                .containsExactly(200, 200, 403);
        assertThat(search.body()).contains(LABEL_MARK, CELL_MARK);
        List<String> marks = List.of(
                VALUE_MARK, LABEL_MARK, CELL_MARK, SEARCH_MARK, TGT + "\"", sample.schema(), sample.reader(), SRC_API);
        assertThat(SpanCollector.NAMES).as("スパンが集まっている（集め手が働いている）").isNotEmpty();
        assertThat(SpanCollector.ATTRIBUTES)
                .allSatisfy(attribute -> assertThat(attribute).doesNotContain(marks));
        List<String> uris = SpanCollector.ATTRIBUTES.stream()
                .filter(attribute -> attribute.startsWith("http.route=") || attribute.startsWith("uri="))
                .filter(attribute -> attribute.contains("/api/tables"))
                .distinct()
                .toList();
        assertThat(uris)
                .isNotEmpty()
                .allSatisfy(uri -> assertThat(uri).endsWith("=" + BusinessDataPaths.REFERENCE_ROWS));
        List<String> tags = app.bean(MeterRegistry.class).find("http.server.requests").meters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .toList();
        assertThat(tags).contains("uri=" + BusinessDataPaths.REFERENCE_ROWS);
        assertThat(tags).allSatisfy(tag -> assertThat(tag).doesNotContain(marks));
    }
}
