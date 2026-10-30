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

import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.WS_MAIN;
import static cherry.mastersmith.businessdata.testsupport.ReferenceTables.W_TGT;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.testsupport.BusinessDataSampleSchema;
import cherry.mastersmith.businessdata.testsupport.BusinessDataTestApp;
import cherry.mastersmith.businessdata.testsupport.ReferenceTables;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.targetdb.domain.DatabaseProduct;
import cherry.mastersmith.targetdb.testsupport.ContainerRuntimeCheck;
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
 * 保存前の参照の確かめのトレースに、参照の値・スキーマ名が残らないことの結合テスト（Intent 261029-reference-lookup-cleanup の U2、計画の
 * Step 10、NFR1.30、既存の {@code BusinessDataWriteTraceExposureIT} と同じ形）。PostgreSQL のコンテナ。終わったスパンの属性をテストだけの
 * 集め手で集め、道が書き込みの固定の道だけで、目印が無いことを確かめる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ContainerRuntimeCheck.class)
class ReferenceWriteTraceExposureIT {

    /** 参照先の行の値の目印。 */
    private static final String TARGET_MARK = "zz-reft-target-5c02";

    /** 本文の参照の値の目印（参照先に無い）。 */
    private static final String BODY_MARK = "zz-reft-body-91e7";

    /** 終わったスパンの属性をすべて集めるテストだけの部品。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCollector {

        static final List<String> ATTRIBUTES = new CopyOnWriteArrayList<>();

        static final List<String> NAMES = new CopyOnWriteArrayList<>();

        @Bean
        SpanProcessor referenceWriteCollectingSpanProcessor() {
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
        tables.createWrite();
        sample.addObjects("INSERT INTO %s" + W_TGT + " (id, v, l) VALUES (91, '" + TARGET_MARK + "', 't')");
        app = BusinessDataTestApp.start(
                dir.resolve("main"), sample.target(), sample.writer(), new Class<?>[] {SpanCollector.class});
        app.installDsl(sample.model(tables.writeTables()));
        member = app.reader(tables.writeSettings(Map.of()));
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
        Map<String, Object> body = sample.body(WS_MAIN);
        body.put("definitionId", app.definitionId(member.token(), sample.schema(), WS_MAIN));
        return body;
    }

    @Test
    @DisplayName("span attributes carry only the fixed write path and no reference value or schema name")
    void noValuesInTraces() {
        SpanCollector.ATTRIBUTES.clear();
        SpanCollector.NAMES.clear();

        Map<String, Object> create = body();
        create.put("values", Map.of("id", "7001", "ref", TARGET_MARK));
        HttpResponse<String> created = app.post(BusinessDataPaths.ROWS, member.token(), create);
        Map<String, Object> rejected = body();
        rejected.put("values", Map.of("id", "7002", "ref", BODY_MARK));
        HttpResponse<String> refused = app.post(BusinessDataPaths.ROWS, member.token(), rejected);
        Map<String, Object> update = body();
        update.put("key", Map.of("id", "7001"));
        update.put("values", Map.of("ref", BODY_MARK));
        HttpResponse<String> updated = app.patch(BusinessDataPaths.ROWS, member.token(), update);

        assertThat(List.of(created, refused, updated))
                .extracting(HttpResponse::statusCode)
                .containsExactly(201, 409, 409);
        List<String> marks =
                List.of(TARGET_MARK, BODY_MARK, sample.schema(), sample.writer().user());
        assertThat(SpanCollector.NAMES).as("スパンが集まっている").isNotEmpty();
        assertThat(SpanCollector.ATTRIBUTES)
                .allSatisfy(attribute -> assertThat(attribute).doesNotContain(marks));
        List<String> uris = SpanCollector.ATTRIBUTES.stream()
                .filter(attribute -> attribute.startsWith("http.route=") || attribute.startsWith("uri="))
                .filter(attribute -> attribute.contains("/api/tables/rows"))
                .distinct()
                .toList();
        assertThat(uris).isNotEmpty().allSatisfy(uri -> assertThat(uri).endsWith("=" + BusinessDataPaths.ROWS));
    }
}
