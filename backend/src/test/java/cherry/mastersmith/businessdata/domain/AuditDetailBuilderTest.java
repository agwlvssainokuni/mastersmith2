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
package cherry.mastersmith.businessdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 監査の detail を作る関数の単体テスト（Intent 261016-business-data-views の U2、BR11.4）。値はすべて架空。 */
class AuditDetailBuilderTest {

    /** 1カラム {@code k} の detail で、値のほかに使う文字の数（{"key":{"k":""},"keyTruncated":false}）。 */
    private static final int OVERHEAD = "{\"key\":{\"k\":\"\"},\"keyTruncated\":false}".length();

    @Test
    @DisplayName("a short key is written as JSON with keyTruncated false")
    void shortKey() {
        Map<String, String> key = new LinkedHashMap<>();
        key.put("a", "1");
        key.put("b", "x\"y\\z\n");

        AuditDetailBuilder.AuditDetail detail = AuditDetailBuilder.build(key);

        assertThat(detail.json()).isEqualTo("{\"key\":{\"a\":\"1\",\"b\":\"x\\\"y\\\\z\\n\"},\"keyTruncated\":false}");
        assertThat(detail.keyColumnCount()).isEqualTo(2);
        assertThat(detail.keyTruncated()).isFalse();
        assertThat(detail.toString()).doesNotContain("x\"y");
    }

    @Test
    @DisplayName("a detail of exactly 16,384 characters is kept as it is")
    void exactlyTheLimit() {
        String value = "v".repeat(AuditDetailBuilder.MAX_LENGTH - OVERHEAD);

        AuditDetailBuilder.AuditDetail detail = AuditDetailBuilder.build(Map.of("k", value));

        assertThat(detail.json()).hasSize(AuditDetailBuilder.MAX_LENGTH).contains(value);
        assertThat(detail.keyTruncated()).isFalse();
    }

    @Test
    @DisplayName("a detail one character over the limit is cut to the limit and marked as truncated")
    void overTheLimit() {
        String value = "v".repeat(AuditDetailBuilder.MAX_LENGTH - OVERHEAD + 1);

        AuditDetailBuilder.AuditDetail detail = AuditDetailBuilder.build(Map.of("k", value));

        assertThat(detail.json().length()).isLessThanOrEqualTo(AuditDetailBuilder.MAX_LENGTH);
        assertThat(detail.json()).endsWith(",\"keyTruncated\":true}");
        assertThat(detail.keyTruncated()).isTrue();
    }

    @Test
    @DisplayName("a composite key is cut from its last column and surrogate pairs are not split")
    void compositeKeyFromTheBack() {
        Map<String, String> key = new LinkedHashMap<>();
        key.put("first", "先頭");
        key.put("second", "😀".repeat(9_000));

        AuditDetailBuilder.AuditDetail detail = AuditDetailBuilder.build(key);

        assertThat(detail.keyTruncated()).isTrue();
        assertThat(detail.json()).contains("\"first\":\"先頭\"");
        assertThat(detail.json().length()).isLessThanOrEqualTo(AuditDetailBuilder.MAX_LENGTH);
        String second = detail.json().substring(detail.json().indexOf("\"second\":\"") + 10);
        String cut = second.substring(0, second.indexOf('"'));
        assertThat(cut.codePoints().allMatch(c -> c == "😀".codePointAt(0))).isTrue();
        assertThat(detail.keyColumnCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a table without a key, as in a create without a primary key, has no detail")
    void noKey() {
        AuditDetailBuilder.AuditDetail detail = AuditDetailBuilder.build(Map.of());

        assertThat(detail.json()).isNull();
        assertThat(detail.keyColumnCount()).isZero();
        assertThat(detail.keyTruncated()).isFalse();
    }
}
