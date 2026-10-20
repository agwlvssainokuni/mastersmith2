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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 乱数の欄を伏せる関数のテスト（Intent 261016-business-data-views の U3 の B6、generation-notes の N-32）。 */
class RandomFieldMaskTest {

    /** 実際に落ちた応答と同じ形（トレースID に 838 を含み、type の番号にも 838 を含む） */
    private static final String BODY = "{\"type\":\"http://localhost:58380/api/problems/business-target-db-mismatch\","
            + "\"status\":409,\"code\":\"BUSINESS_TARGET_DB_MISMATCH\",\"traceId\":\"b753f2c80b5669f1c83841ced957c5d3\"}";

    @Test
    @DisplayName("a bare digit mark hits the random trace id and the port of the type without masking")
    void unmaskedBodyHitsRandomFields() {
        assertThat(BODY).contains("838");
    }

    @Test
    @DisplayName("masking removes the trace id and the port so the mark no longer hits them")
    void maskedBodyDoesNotHitRandomFields() {
        String masked = RandomFieldMask.mask(BODY);
        assertThat(masked)
                .doesNotContain("838")
                .contains("\"traceId\":\"<id>\"")
                .contains("http://localhost:<port>/api/problems/business-target-db-mismatch")
                .contains("\"code\":\"BUSINESS_TARGET_DB_MISMATCH\"");
    }

    @Test
    @DisplayName("masking keeps a leaked row value in the other fields")
    void maskingKeepsLeakedValues() {
        String leaked = BODY.replace("\"status\":409", "\"detail\":\"838:59:59\",\"status\":409");
        assertThat(RandomFieldMask.mask(leaked)).contains("838:59:59");
        String log = "{\"message\":\"value 98765431\",\"traceId\":\"98765431ab\",\"spanId\":\"0098765431\","
                + "\"auditTraceId\":\"98765431cd\"}";
        String traced = "{\"message\":\"EXIT x(): ProblemDetail[properties='{code=X, traceId=98765431ab}'], "
                + "WriteOrigin[requestPath=/api/tables/rows, traceId=0098765431]\"}";
        assertThat(RandomFieldMask.mask(traced)).doesNotContain("98765431").contains("traceId=<id>");
        assertThat(RandomFieldMask.mask(log))
                .contains("value 98765431")
                .contains("\"traceId\":\"<id>\"")
                .contains("\"spanId\":\"<id>\"")
                .contains("\"auditTraceId\":\"<id>\"");
    }

    @Test
    @DisplayName("masking keeps the host and the port of the target database outside the problem type")
    void maskingKeepsTargetEndpoint() {
        String log = "{\"message\":\"connect localhost:58380 failed\"}";
        assertThat(RandomFieldMask.mask(log)).contains("localhost:58380");
    }

    @Test
    @DisplayName("masking leaves a text without random fields unchanged")
    void unchangedWithoutRandomFields() {
        String text = "{\"detail\":\"見本\",\"instance\":\"/api/tables/rows/get\"}";
        assertThat(RandomFieldMask.mask(text)).isEqualTo(text);
    }
}
