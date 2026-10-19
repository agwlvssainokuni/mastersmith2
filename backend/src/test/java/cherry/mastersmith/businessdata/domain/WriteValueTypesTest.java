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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 書き込みの値を持つ型の単体テスト（Intent 261016-business-data-views の U2、BR12.1）。値はすべて架空。 */
class WriteValueTypesTest {

    private static final String SECRET = "秘密の値-123";

    private static final WriteOrigin ORIGIN = new WriteOrigin("192.0.2.1", "agent", "/api/tables/rows", null);

    @Test
    @DisplayName("the request and its values show only counts, never names or values")
    void requestHidesValues() {
        WriteValues values = new WriteValues(Map.of("name", SECRET));
        WriteRequest request = new WriteRequest(
                new TableQuery("sample", "item", null),
                WriteOperation.UPDATE,
                new KeyInput(Map.of("id", SECRET)),
                values);

        assertThat(values.toString()).isEqualTo("WriteValues[columns=1]");
        assertThat(request.toString())
                .isEqualTo("WriteRequest[operation=UPDATE, keyColumns=1, valueColumns=1]")
                .doesNotContain(SECRET);
        assertThat(new WriteRequest(new TableQuery("s", "t", null), WriteOperation.CREATE, null, null)
                        .key()
                        .values())
                .isEmpty();
    }

    @Test
    @DisplayName("the values are copied and cannot be changed afterwards")
    void valuesAreImmutable() {
        Map<String, String> source = new HashMap<>();
        source.put("a", null);
        WriteValues values = new WriteValues(source);
        source.put("b", "x");

        assertThat(values.values()).containsOnlyKeys("a").containsEntry("a", null);
        assertThatThrownBy(() -> values.values().put("c", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a typed value shows the column and the type only and rejects OTHER")
    void typedValue() {
        TypedValue value = new TypedValue("name", new BoundValue(ValueType.STRING, SECRET));

        assertThat(value.toString())
                .isEqualTo("TypedValue[column=name, valueType=STRING, value=***]")
                .doesNotContain(SECRET);
        assertThat(new TypedValue("n", new BoundValue(ValueType.INTEGER, null)).toString())
                .endsWith("value=null]");
        assertThatThrownBy(() -> new TypedValue("x", new BoundValue(ValueType.OTHER, "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the change event hides the key detail and checks that the result matches the reason")
    void changeEvent() {
        AuditDetailBuilder.AuditDetail detail = AuditDetailBuilder.build(Map.of("id", SECRET));
        BusinessDataChangedEvent event = BusinessDataChangedEvent.of(
                1L, 7L, "sample", "item", WriteOperation.DELETE, null, detail, Instant.EPOCH, ORIGIN);

        assertThat(event.result()).isEqualTo(BusinessDataAuditResult.SUCCESS);
        assertThat(event.detail()).contains(SECRET);
        assertThat(event.toString()).doesNotContain(SECRET).contains("keyColumns=1");
        assertThat(BusinessDataChangedEvent.of(
                                1L,
                                null,
                                "s",
                                "t",
                                WriteOperation.UPDATE,
                                BusinessDataAuditReason.BUSINESS_TARGET_DB_TIMEOUT,
                                detail,
                                Instant.EPOCH,
                                ORIGIN)
                        .result())
                .isEqualTo(BusinessDataAuditResult.UNKNOWN);
        assertThatThrownBy(() -> new BusinessDataChangedEvent(
                        1L,
                        null,
                        "s",
                        "t",
                        WriteOperation.CREATE,
                        BusinessDataAuditResult.SUCCESS,
                        BusinessDataAuditReason.BUSINESS_ROW_NOT_FOUND,
                        null,
                        0,
                        false,
                        Instant.EPOCH,
                        ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessDataChangedEvent(
                        1L,
                        null,
                        "s",
                        "t",
                        WriteOperation.CREATE,
                        BusinessDataAuditResult.UNKNOWN,
                        BusinessDataAuditReason.BUSINESS_ROW_NOT_FOUND,
                        null,
                        0,
                        false,
                        Instant.EPOCH,
                        ORIGIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the origin cuts a long user agent and requires the source address")
    void origin() {
        assertThat(new WriteOrigin("192.0.2.1", "x".repeat(600), null, null).userAgent())
                .hasSize(512);
        assertThatThrownBy(() -> new WriteOrigin(null, null, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("failures, foreign keys and validations keep only codes and counts")
    void failuresAndForeignKeys() {
        RowWriteFailure failure =
                new RowWriteFailure(RowWriteFailureReason.DUPLICATE, RowWriteStage.WRITE, "23505", 0, "E");
        ForeignKeyRef key = new ForeignKeyRef(List.of("a"), "ref", List.of("id"), java.util.Set.of("id"));

        assertThat(failure.reason()).isEqualTo(RowWriteFailureReason.DUPLICATE);
        assertThat(key.toString()).isEqualTo("ForeignKeyRef[columns=1]");
        assertThatThrownBy(() -> new ForeignKeyRef(List.of("a", "b"), "ref", List.of("id"), java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WriteValidation.Invalid(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new WriteValidation.Valid(List.of(), null).toString()).isEqualTo("Valid[values=0, key=null]");
        assertThat(WriteOperation.UPDATE.allowedOperation()).isEqualTo(AllowedOperation.UPDATE);
    }
}
