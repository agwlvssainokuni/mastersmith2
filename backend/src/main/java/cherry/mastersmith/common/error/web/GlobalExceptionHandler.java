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
package cherry.mastersmith.common.error.web;

import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 例外をエラー応答へ変換する1か所（BR5.1、BR5.5〜BR5.8、BR5.16）。個々のコントローラーでエラー応答を組み立てない。
 *
 * <p>ログは変換するここで1回だけ出す。想定内（4xx と業務エラー）は WARN でスタックトレースなし、想定外（5xx）は ERROR でスタックトレース
 * 付き。業務エラー（{@link BusinessException}）は、状態コードが 5xx（例: 対象DB に接続できない 503）でも想定内の失敗として WARN に
 * する（Intent 260923-dsl-schema-loader の U4 の NFR5.3）。業務エラーの追加の項目は応答に載せる（BR8.1）。
 * 応答に例外のメッセージとスタックトレースを載せない。フレームワークの標準の 4xx は、状態コードを保って専用の code にする（計画の
 * P1 の決定）。変換の対象として決めていない例外は 500 / {@code INTERNAL_ERROR} にする。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory factory;

    /**
     * 変換の仕組みを作る。
     *
     * @param factory ErrorResponse の組み立て
     */
    public GlobalExceptionHandler(ErrorResponseFactory factory) {
        this.factory = factory;
    }

    /**
     * 業務エラーを、その問題の種類のエラー応答にする。
     *
     * @param ex 業務エラー
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log(ex, ex.getProblemType(), true);
        return build(request, ex.getProblemType(), ex.getDetail(), ex.getProperties(), null);
    }

    /**
     * 入力の検証の失敗を 400 / {@code VALIDATION_FAILED} にする（BR5.7）。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        TypeMismatchException.class,
        ServletRequestBindingException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ProblemDetail> handleValidation(Exception ex, HttpServletRequest request) {
        return respond(ex, request, CommonProblemTypes.VALIDATION_FAILED, null, null);
    }

    /**
     * 読み取れない要求の本文を 400 / {@code MALFORMED_REQUEST} にする。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformed(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(ex, request, CommonProblemTypes.MALFORMED_REQUEST, null, null);
    }

    /**
     * 存在しない API を 404 / {@code NOT_FOUND} にする（BR5.8）。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(Exception ex, HttpServletRequest request) {
        return respond(ex, request, CommonProblemTypes.NOT_FOUND, null, null);
    }

    /**
     * 許されない HTTP メソッドを 405 / {@code METHOD_NOT_ALLOWED} にし、使えるメソッドを Allow ヘッダーで示す。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            headers.setAllow(supported);
        }
        return respond(ex, request, CommonProblemTypes.METHOD_NOT_ALLOWED, null, headers);
    }

    /**
     * 求められた形式で応答を返せない要求を 406 / {@code NOT_ACCEPTABLE} にする（本文は {@code application/problem+json}）。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return respond(ex, request, CommonProblemTypes.NOT_ACCEPTABLE, null, null);
    }

    /**
     * 対応していない本文の形式を 415 / {@code UNSUPPORTED_MEDIA_TYPE} にする。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ex, request, CommonProblemTypes.UNSUPPORTED_MEDIA_TYPE, null, null);
    }

    /**
     * 本文の大きさの上限を超えた要求を 413 / {@code PAYLOAD_TOO_LARGE} にする。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handlePayloadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(ex, request, CommonProblemTypes.PAYLOAD_TOO_LARGE, null, null);
    }

    /**
     * 変換の対象として決めていない例外を 500 / {@code INTERNAL_ERROR} にする（BR5.6）。
     *
     * @param ex 例外
     * @param request 要求
     * @return エラー応答
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        return respond(ex, request, CommonProblemTypes.INTERNAL_ERROR, null, null);
    }

    private ResponseEntity<ProblemDetail> respond(
            Exception ex, HttpServletRequest request, ProblemType type, String detail, HttpHeaders headers) {
        log(ex, type, false);
        return build(request, type, detail, Map.of(), headers);
    }

    private ResponseEntity<ProblemDetail> build(
            HttpServletRequest request,
            ProblemType type,
            String detail,
            Map<String, Object> properties,
            HttpHeaders headers) {
        ProblemDetail body = factory.create(request, type, detail, properties);
        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(type.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (headers != null) {
            builder.headers(headers);
        }
        return builder.body(body);
    }

    private static void log(Exception ex, ProblemType type, boolean expected) {
        if (!expected && type.status() >= 500) {
            LOGGER.atError()
                    .setCause(ex)
                    .addKeyValue("code", type.code())
                    .addKeyValue("status", type.status())
                    .log("想定外のエラーが起きました");
        } else {
            LOGGER.atWarn()
                    .addKeyValue("code", type.code())
                    .addKeyValue("status", type.status())
                    .addKeyValue("exceptionType", ex.getClass().getName())
                    .log("要求をエラー応答に変換しました");
        }
    }
}
