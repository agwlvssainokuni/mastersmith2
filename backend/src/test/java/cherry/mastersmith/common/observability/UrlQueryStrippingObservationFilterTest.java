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
package cherry.mastersmith.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlQueryStrippingObservationFilterTest {

    private final UrlQueryStrippingObservationFilter filter = new UrlQueryStrippingObservationFilter();

    private static String value(Observation.Context context, String key) {
        KeyValue keyValue = context.getHighCardinalityKeyValue(key);
        if (keyValue == null) {
            keyValue = context.getLowCardinalityKeyValue(key);
        }
        return keyValue == null ? null : keyValue.getValue();
    }

    @Test
    @DisplayName("query part is removed from URL attributes")
    void queryRemovedFromUrl() {
        Observation.Context context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "/api/problems/x?token=secret"));
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/x?password=secret"));

        filter.map(context);

        assertThat(value(context, "http.url")).isEqualTo("/api/problems/x");
        assertThat(value(context, "uri")).isEqualTo("/api/x");
    }

    @Test
    @DisplayName("URL without a query part stays unchanged")
    void urlWithoutQueryUnchanged() {
        Observation.Context context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("url.full", "http://localhost/api/problems/not-found"));

        filter.map(context);

        assertThat(value(context, "url.full")).isEqualTo("http://localhost/api/problems/not-found");
    }

    @Test
    @DisplayName("attributes that only hold the query are removed")
    void queryOnlyAttributesRemoved() {
        Observation.Context context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("url.query", "token=secret"));

        filter.map(context);

        assertThat(value(context, "url.query")).isNull();
    }

    @Test
    @DisplayName("context without URL attributes does not fail")
    void noUrlAttributes() {
        Observation.Context context = new Observation.Context();

        assertThat(filter.map(context)).isSameAs(context);
        assertThat(context.getAllKeyValues()).isEmpty();
    }

    @Test
    @DisplayName("other attributes are left untouched even if they contain a question mark")
    void otherAttributesUntouched() {
        Observation.Context context = new Observation.Context();
        context.addLowCardinalityKeyValue(KeyValue.of("method", "GET"));
        context.addHighCardinalityKeyValue(KeyValue.of("note", "why?"));

        filter.map(context);

        assertThat(value(context, "method")).isEqualTo("GET");
        assertThat(value(context, "note")).isEqualTo("why?");
    }
}
