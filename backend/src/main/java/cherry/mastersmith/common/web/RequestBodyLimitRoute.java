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
package cherry.mastersmith.common.web;

import cherry.mastersmith.common.error.domain.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Objects;

/**
 * 要求の本文の大きさの上限を、特定の道（HTTP メソッドとパス）だけ変える決まり（{@link RequestSizeLimitFilter} の道ごとの上限）。
 *
 * <p>各機能が Bean として置き、{@code SecurityConfig} が集めて本文の上限の仕組みに渡す。共通の部品（{@code common.web}）は、
 * どの機能の道かを知らない。パスはコンテキストパスを除いたアプリの中のパスで、完全に一致したときだけ当たる。
 *
 * @param method HTTP メソッド（大文字）
 * @param path アプリの中のパス（例 {@code /api/admin/dsl/preview}）
 * @param maxBytes 本文の大きさの上限（バイト、1 以上）
 * @param problemType 上限を超えたときの問題の種類（状態コードは 413）
 */
public record RequestBodyLimitRoute(String method, String path, long maxBytes, ProblemType problemType) {

    /** 値を確かめる。 */
    public RequestBodyLimitRoute {
        method = Objects.requireNonNull(method, "method は必須です").toUpperCase(Locale.ROOT);
        Objects.requireNonNull(path, "path は必須です");
        Objects.requireNonNull(problemType, "problemType は必須です");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes は 1 以上にしてください: " + maxBytes);
        }
        if (problemType.status() != 413) {
            throw new IllegalArgumentException("problemType の状態コードは 413 にしてください: " + problemType.code());
        }
    }

    /**
     * 要求がこの道に当たるかを返す。
     *
     * @param request 要求
     * @return 当たれば true
     */
    public boolean matches(HttpServletRequest request) {
        return method.equals(request.getMethod()) && path.equals(RequestSizeLimitFilter.relativePath(request));
    }
}
