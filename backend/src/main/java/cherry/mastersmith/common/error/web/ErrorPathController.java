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

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC の外（サーブレットコンテナやフィルター）で起きたエラーの応答（{@code /error}）を、共通のエラー応答の形にする（BR5.1）。
 *
 * <p>Spring Boot の既定のエラー応答（BasicErrorController）の代わりに使う。状態コードに対応する問題の種類を当て、対応の無いものは
 * 500 / {@code INTERNAL_ERROR} にする。例外のメッセージとスタックトレースは応答に載せない。
 */
@RestController
public class ErrorPathController implements ErrorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorPathController.class);

    private final ErrorResponseFactory factory;

    /**
     * エラーの応答を作る。
     *
     * @param factory ErrorResponse の組み立て
     */
    public ErrorPathController(ErrorResponseFactory factory) {
        this.factory = factory;
    }

    /**
     * 読み取りのメソッド（GET・HEAD・OPTIONS）の要求のエラーの応答を返す。
     *
     * @param request エラーの転送（ERROR の dispatch）の要求
     * @return エラー応答
     */
    @RequestMapping(
            path = "${server.error.path:/error}",
            method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        return respond(request);
    }

    /**
     * 更新のメソッド（POST・PUT・PATCH・DELETE）の要求のエラーの応答を返す。エラーの転送は元の要求のメソッドのまま届くため、
     * 読み取りのメソッドと分けて受ける（どちらも状態は変えず、エラー応答を返すだけ）。
     *
     * @param request エラーの転送（ERROR の dispatch）の要求
     * @return エラー応答
     */
    @RequestMapping(
            path = "${server.error.path:/error}",
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> errorForUpdate(HttpServletRequest request) {
        return respond(request);
    }

    private ResponseEntity<ProblemDetail> respond(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        ProblemType type = toProblemType(status instanceof Integer code ? code : 500);
        Object error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (type.status() >= 500) {
            LOGGER.atError()
                    .setCause(error instanceof Throwable throwable ? throwable : null)
                    .addKeyValue("code", type.code())
                    .addKeyValue("status", type.status())
                    .log("想定外のエラーが起きました");
        } else {
            LOGGER.atWarn()
                    .addKeyValue("code", type.code())
                    .addKeyValue("status", type.status())
                    .log("要求をエラー応答に変換しました");
        }
        return ResponseEntity.status(type.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.create(request, type, null));
    }

    /**
     * 状態コードに対応する問題の種類を返す。
     *
     * @param status 状態コード
     * @return 問題の種類
     */
    static ProblemType toProblemType(int status) {
        return switch (status) {
            case 400 -> CommonProblemTypes.MALFORMED_REQUEST;
            case 404 -> CommonProblemTypes.NOT_FOUND;
            case 405 -> CommonProblemTypes.METHOD_NOT_ALLOWED;
            case 406 -> CommonProblemTypes.NOT_ACCEPTABLE;
            case 413 -> CommonProblemTypes.PAYLOAD_TOO_LARGE;
            case 415 -> CommonProblemTypes.UNSUPPORTED_MEDIA_TYPE;
            default -> CommonProblemTypes.INTERNAL_ERROR;
        };
    }
}
