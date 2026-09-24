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

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 要求の本文の大きさを確かめる（NFR3.12）。Spring Security のフィルターの連鎖の中、認証（アクセストークンの検証）と認可の後に置く
 * （Intent 260923-dsl-schema-loader の U4 の NFR 設計の決定 A。本文を読むのは、ログインしていてその道を使える人の要求だけになる）。
 *
 * <p>{@code Content-Length} が上限を超えれば、本文を読まずに 413 を返す。{@code Content-Length} が無い送り方（分割の送信）では、
 * 読んだ量を数え、上限を超えた時点で読むのをやめて 413 を返す（上限までの本文は読み込んで後ろへ渡す）。応答は
 * {@link ErrorResponseWriter} で ErrorResponse の形にする。
 *
 * <p>上限は既定の値（{@code mastersmith.web.max-request-body-size}、code {@code PAYLOAD_TOO_LARGE}）で、道ごとの決まり
 * （{@link RequestBodyLimitRoute}）に当たる要求だけ、その上限と code にする。断ったときは {@link RequestSizeRejectionListener} に
 * 知らせる（道・上限・送られた大きさだけ。本文の中身は渡さない）。
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private static final int BUFFER_SIZE = 8192;

    private final long maxBytes;

    private final List<RequestBodyLimitRoute> routes;

    private final List<RequestSizeRejectionListener> listeners;

    private final ErrorResponseWriter errorResponseWriter;

    /**
     * 道ごとの決まりの無い本文の大きさの確認を作る。
     *
     * @param maxBytes 本文の大きさの上限（バイト）
     * @param errorResponseWriter フィルターの段階のエラー応答の書き方
     */
    public RequestSizeLimitFilter(long maxBytes, ErrorResponseWriter errorResponseWriter) {
        this(maxBytes, List.of(), List.of(), errorResponseWriter);
    }

    /**
     * 本文の大きさの確認を作る。
     *
     * @param maxBytes 既定の本文の大きさの上限（バイト）
     * @param routes 道ごとの上限と code（先に当たったものを使う）
     * @param listeners 断ったことの受け取り
     * @param errorResponseWriter フィルターの段階のエラー応答の書き方
     */
    public RequestSizeLimitFilter(
            long maxBytes,
            List<RequestBodyLimitRoute> routes,
            List<RequestSizeRejectionListener> listeners,
            ErrorResponseWriter errorResponseWriter) {
        this.maxBytes = maxBytes;
        this.routes = List.copyOf(routes);
        this.listeners = List.copyOf(listeners);
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RequestBodyLimitRoute route = routes.stream()
                .filter(candidate -> candidate.matches(request))
                .findFirst()
                .orElse(null);
        long limit = route == null ? maxBytes : route.maxBytes();
        ProblemType problemType = route == null ? CommonProblemTypes.PAYLOAD_TOO_LARGE : route.problemType();
        long contentLength = request.getContentLengthLong();
        if (contentLength > limit) {
            reject(request, response, new Limit(limit, problemType, route), contentLength);
            return;
        }
        if (contentLength < 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            byte[] body = readUpToLimit(request.getInputStream(), limit);
            if (body == null) {
                reject(request, response, new Limit(limit, problemType, route), -1);
                return;
            }
            chain.doFilter(new BufferedBodyRequest(request, body), response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 上限までの本文を読む。上限を超えた時点で読むのをやめ、null を返す。 */
    private static byte[] readUpToLimit(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        while (true) {
            // 上限＋1バイトより先は読まない（超えたと分かった時点で読むのをやめる）。
            int toRead = (int) Math.min(buffer.length, maxBytes + 1 - total);
            int read = input.read(buffer, 0, toRead);
            if (read == -1) {
                return body.toByteArray();
            }
            total += read;
            if (total > maxBytes) {
                return null;
            }
            body.write(buffer, 0, read);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Limit limit, long contentLength)
            throws IOException {
        LOGGER.atWarn()
                .addKeyValue("code", limit.problemType().code())
                .addKeyValue("status", limit.problemType().status())
                .addKeyValue("contentLength", contentLength)
                .addKeyValue("maxBytes", limit.maxBytes())
                .log("要求の本文が大きさの上限を超えました");
        RequestSizeRejection rejection = new RequestSizeRejection(
                request.getMethod(),
                relativePath(request),
                limit.maxBytes(),
                contentLength,
                limit.problemType(),
                limit.route());
        for (RequestSizeRejectionListener listener : listeners) {
            try {
                listener.onRejected(request, rejection);
            } catch (RuntimeException e) {
                // 受け取り側の失敗で応答を変えない。黙って捨てず、例外の型だけを WARN で出す。
                LOGGER.atWarn()
                        .addKeyValue("exceptionType", e.getClass().getName())
                        .log("本文の上限で断ったことの受け取りで例外が戻りました");
            }
        }
        errorResponseWriter.write(request, response, limit.problemType());
    }

    /**
     * 要求の URI からコンテキストパスを除いた、アプリの中のパスを返す。
     *
     * @param request 要求
     * @return アプリの中のパス
     */
    static String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri != null && contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /** 要求に当てた上限。 */
    private record Limit(long maxBytes, ProblemType problemType, RequestBodyLimitRoute route) {}

    /** 読み込んだ本文を後ろの処理へ渡すための要求の包み。 */
    static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("非同期の読み込みには対応していません");
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return input.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
        }
    }
}
