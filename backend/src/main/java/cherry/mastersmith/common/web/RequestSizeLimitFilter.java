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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 要求の本文の大きさを確かめる（NFR3.12）。Spring Security のフィルターの連鎖の中、ヘッダーを書く処理の後に置く。
 *
 * <p>{@code Content-Length} が上限を超えれば、本文を読まずに 413 / {@code PAYLOAD_TOO_LARGE} を返す。{@code Content-Length} が無い
 * 送り方（分割の送信）では、読んだ量を数え、上限を超えた時点で読むのをやめて 413 を返す（上限までの本文は読み込んで後ろへ渡す）。
 * 応答は {@link ErrorResponseWriter} で ErrorResponse の形にする。
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private static final int BUFFER_SIZE = 8192;

    private final long maxBytes;

    private final ErrorResponseWriter errorResponseWriter;

    /**
     * 本文の大きさの確認を作る。
     *
     * @param maxBytes 本文の大きさの上限（バイト）
     * @param errorResponseWriter フィルターの段階のエラー応答の書き方
     */
    public RequestSizeLimitFilter(long maxBytes, ErrorResponseWriter errorResponseWriter) {
        this.maxBytes = maxBytes;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            reject(request, response, contentLength);
            return;
        }
        if (contentLength < 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            byte[] body = readUpToLimit(request.getInputStream());
            if (body == null) {
                reject(request, response, -1);
                return;
            }
            chain.doFilter(new BufferedBodyRequest(request, body), response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 上限までの本文を読む。上限を超えた時点で読むのをやめ、null を返す。 */
    private byte[] readUpToLimit(InputStream input) throws IOException {
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

    private void reject(HttpServletRequest request, HttpServletResponse response, long contentLength)
            throws IOException {
        LOGGER.atWarn()
                .addKeyValue("code", CommonProblemTypes.PAYLOAD_TOO_LARGE.code())
                .addKeyValue("status", CommonProblemTypes.PAYLOAD_TOO_LARGE.status())
                .addKeyValue("contentLength", contentLength)
                .addKeyValue("maxBytes", maxBytes)
                .log("要求の本文が大きさの上限を超えました");
        errorResponseWriter.write(request, response, CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

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
