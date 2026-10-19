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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cherry.mastersmith.businessdata.domain.BusinessDataFieldError;
import cherry.mastersmith.businessdata.domain.BusinessDataFieldErrorReason;
import cherry.mastersmith.businessdata.domain.KeyInput;
import cherry.mastersmith.businessdata.domain.RequestedDefinitionId;
import cherry.mastersmith.businessdata.domain.RowValues;
import cherry.mastersmith.businessdata.domain.TableQuery;
import cherry.mastersmith.businessdata.domain.WriteOperation;
import cherry.mastersmith.businessdata.domain.WriteOrigin;
import cherry.mastersmith.businessdata.domain.WriteRejection;
import cherry.mastersmith.businessdata.domain.WriteRequest;
import cherry.mastersmith.businessdata.domain.WriteValues;
import cherry.mastersmith.businessdata.service.BusinessDataWriteService;
import cherry.mastersmith.businessdata.service.WriteResult;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.error.service.ProblemTypeRegistry;
import cherry.mastersmith.common.observability.TraceIdProvider;
import cherry.mastersmith.common.security.ApiAccess;
import cherry.mastersmith.common.security.ApiAccessLevel;
import cherry.mastersmith.dsl.domain.DisplayName;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 書き込みの API の結果の型から応答・業務エラーへの変換の単体テスト（Intent 261016-business-data-views の U2、BR1.1〜BR1.9・BR12.1・
 * BR12.3・BR13.1）。値はすべて架空。
 */
class BusinessDataWriteControllerTest {

    private static final ProblemType ACCESS_DENIED = new ProblemType(
            "ACCESS_DENIED", 403, new LocalizedText("拒否", "Denied"), new LocalizedText("拒否", "Denied"), null);

    private static final String SECRET = "行の値-秘密";

    private static final DefinitionIdBody DEFINITION = new DefinitionIdBody(BusinessDataDslFixture.HASH, 7L);

    private BusinessDataWriteService service;

    private BusinessDataWriteController controller;

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = mock(BusinessDataWriteService.class);
        BusinessDataRequestContext context = mock(BusinessDataRequestContext.class);
        when(context.currentUserId()).thenReturn(42L);
        TraceIdProvider traces = mock(TraceIdProvider.class);
        when(traces.currentTraceId()).thenReturn(Optional.of("trace-0070"));
        ProblemTypeRegistry registry = mock(ProblemTypeRegistry.class);
        when(registry.findByCode("ACCESS_DENIED")).thenReturn(Optional.of(ACCESS_DENIED));
        controller = new BusinessDataWriteController(service, context, traces, registry);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.0.2.70");
        when(request.getHeader("User-Agent")).thenReturn("agent");
        when(request.getRequestURI()).thenReturn("/api/tables/rows");
    }

    @org.junit.jupiter.api.DisplayName(
            "a create returns 201 with the definition and the key, sending the body to the service")
    @Test
    void create() {
        when(service.write(eq(42L), any(), any()))
                .thenReturn(new WriteResult.Created(
                        BusinessDataDslFixture.DEFINITION, new RowValues(Map.of("id", "101"), Set.of())));

        RowCreatedResponse response =
                controller.create(new RowCreateRequest("sample", "item", DEFINITION, Map.of("name", SECRET)), request);

        ArgumentCaptor<WriteRequest> sent = ArgumentCaptor.forClass(WriteRequest.class);
        ArgumentCaptor<WriteOrigin> origin = ArgumentCaptor.forClass(WriteOrigin.class);
        verify(service).write(eq(42L), sent.capture(), origin.capture());
        assertThat(sent.getValue())
                .isEqualTo(new WriteRequest(
                        new TableQuery("sample", "item", new RequestedDefinitionId(BusinessDataDslFixture.HASH, 7L)),
                        WriteOperation.CREATE,
                        new KeyInput(null),
                        new WriteValues(Map.of("name", SECRET))));
        assertThat(origin.getValue())
                .isEqualTo(new WriteOrigin("192.0.2.70", "agent", "/api/tables/rows", "trace-0070"));
        assertThat(response).isEqualTo(new RowCreatedResponse(DEFINITION, Map.of("id", "101")));
        assertThat(response.toString()).doesNotContain("101");
    }

    @org.junit.jupiter.api.DisplayName("a create without a returned key omits the key and the endpoints are login-only")
    @Test
    void createWithoutKeyAndAccessLevel() throws NoSuchMethodException {
        when(service.write(eq(42L), any(), any()))
                .thenReturn(
                        new WriteResult.Created(BusinessDataDslFixture.DEFINITION, new RowValues(Map.of(), Set.of())));

        assertThat(controller
                        .create(new RowCreateRequest("sample", "log", DEFINITION, Map.of()), request)
                        .key())
                .isNull();
        assertThat(BusinessDataWriteController.class
                        .getAnnotation(ApiAccess.class)
                        .value())
                .isEqualTo(ApiAccessLevel.AUTHENTICATED);
        assertThat(BusinessDataWriteController.class
                        .getMethod("create", RowCreateRequest.class, HttpServletRequest.class)
                        .getAnnotation(ResponseStatus.class)
                        .value())
                .isEqualTo(HttpStatus.CREATED);
    }

    @org.junit.jupiter.api.DisplayName("update and delete return 200 with the definition and send the key")
    @Test
    void updateAndDelete() {
        when(service.write(eq(42L), any(), any()))
                .thenReturn(new WriteResult.Written(BusinessDataDslFixture.DEFINITION));

        assertThat(controller.update(
                        new RowUpdateRequest("sample", "item", DEFINITION, Map.of("id", "5"), Map.of("note", "x")),
                        request))
                .isEqualTo(new RowWrittenResponse(DEFINITION));
        assertThat(controller.delete(new RowDeleteRequest("sample", "item", DEFINITION, Map.of("id", "5")), request))
                .isEqualTo(new RowWrittenResponse(DEFINITION));
        ArgumentCaptor<WriteRequest> sent = ArgumentCaptor.forClass(WriteRequest.class);
        verify(service, org.mockito.Mockito.times(2)).write(eq(42L), sent.capture(), any());
        assertThat(sent.getAllValues())
                .extracting(WriteRequest::operation)
                .containsExactly(WriteOperation.UPDATE, WriteOperation.DELETE);
        assertThat(sent.getAllValues().getLast().values().values()).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource({
        "DSL_NOT_APPLIED, 404, BUSINESS_DSL_NOT_APPLIED",
        "TABLE_NOT_FOUND, 404, BUSINESS_TABLE_NOT_FOUND",
        "ACCESS_DENIED, 403, ACCESS_DENIED",
        "DEFINITION_CHANGED, 409, BUSINESS_DEFINITION_CHANGED",
        "ROW_OPERATION_NOT_ALLOWED, 409, BUSINESS_ROW_OPERATION_NOT_ALLOWED",
        "WRITE_NOT_CONFIGURED, 409, BUSINESS_WRITE_NOT_CONFIGURED",
        "VALIDATION_FAILED, 400, VALIDATION_FAILED",
        "TARGET_DB_UNAVAILABLE, 503, BUSINESS_TARGET_DB_UNAVAILABLE",
        "TARGET_DB_MISMATCH, 409, BUSINESS_TARGET_DB_MISMATCH",
        "DUPLICATE_VALUE, 409, BUSINESS_DUPLICATE_VALUE",
        "REFERENCE_NOT_FOUND, 409, BUSINESS_REFERENCE_NOT_FOUND",
        "SAVE_REJECTED, 409, BUSINESS_SAVE_REJECTED",
        "ROW_NOT_FOUND, 404, BUSINESS_ROW_NOT_FOUND",
        "ROW_NOT_UNIQUE, 409, BUSINESS_ROW_NOT_UNIQUE",
        "TARGET_DB_TIMEOUT, 503, BUSINESS_TARGET_DB_TIMEOUT"
    })
    @org.junit.jupiter.api.DisplayName("every rejection becomes its code and status")
    void rejections(WriteRejection rejection, int status, String code) {
        BusinessException e = controller.toException(WriteResult.Rejected.of(rejection));

        assertThat(e.getProblemType().code()).isEqualTo(code);
        assertThat(e.getProblemType().status()).isEqualTo(status);
    }

    @org.junit.jupiter.api.DisplayName(
            "field errors carry the column, the reason and the pattern message only, never values")
    @Test
    void fieldErrors() {
        DisplayName message = new DisplayName("英大文字3つ", "Three capital letters");
        BusinessException invalid = controller.toException(new WriteResult.Rejected(
                WriteRejection.VALIDATION_FAILED,
                List.of(
                        new BusinessDataFieldError("code", BusinessDataFieldErrorReason.PATTERN_MISMATCH, message),
                        new BusinessDataFieldError("", BusinessDataFieldErrorReason.NO_CHANGE))));
        BusinessException duplicate = controller.toException(new WriteResult.Rejected(
                WriteRejection.DUPLICATE_VALUE,
                List.of(new BusinessDataFieldError("name", BusinessDataFieldErrorReason.DUPLICATE))));
        BusinessException rejected = controller.toException(WriteResult.Rejected.of(WriteRejection.SAVE_REJECTED));

        assertThat(invalid.getProblemType()).isEqualTo(CommonProblemTypes.VALIDATION_FAILED);
        assertThat(invalid.getProperties())
                .containsEntry("definitionChanged", false)
                .containsEntry(
                        "fieldErrors",
                        List.of(
                                Map.of(
                                        "field",
                                        "code",
                                        "reason",
                                        "PATTERN_MISMATCH",
                                        "message",
                                        Map.of("ja", "英大文字3つ", "en", "Three capital letters")),
                                Map.of("field", "", "reason", "NO_CHANGE")));
        assertThat(duplicate.getProperties())
                .containsEntry("fieldErrors", List.of(Map.of("field", "name", "reason", "DUPLICATE")));
        assertThat(rejected.getProperties())
                .as("カラムを示さない 409 は fieldErrors を付けない")
                .isEmpty();
    }

    @org.junit.jupiter.api.DisplayName("the request records hide keys and values in their string form")
    @Test
    void requestStringForms() {
        assertThat(new RowCreateRequest("sample", "item", DEFINITION, Map.of("name", SECRET)).toString())
                .isEqualTo("RowCreateRequest[schema=sample, table=item, valueColumns=1]");
        assertThat(new RowUpdateRequest("sample", "item", DEFINITION, Map.of("id", SECRET), Map.of("n", SECRET))
                        .toString())
                .isEqualTo("RowUpdateRequest[schema=sample, table=item, keyColumns=1, valueColumns=1]");
        assertThat(new RowDeleteRequest("sample", "item", DEFINITION, null).toString())
                .isEqualTo("RowDeleteRequest[schema=sample, table=item, keyColumns=0]");
    }
}
