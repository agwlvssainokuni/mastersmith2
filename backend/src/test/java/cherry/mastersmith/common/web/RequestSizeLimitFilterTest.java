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

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

    private static final int LIMIT = 1024;

    private final List<ProblemType> written = new ArrayList<>();

    private final ErrorResponseWriter writer = (request, response, type) -> {
        written.add(type);
        response.setStatus(type.status());
    };

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(LIMIT, writer);

    /** Content-Length を持たない（分割の送信の）要求。読んだバイト数を数える。 */
    private static final class ChunkedRequest extends MockHttpServletRequest {

        private final byte[] body;

        int bytesRead;

        ChunkedRequest(byte[] body) {
            super("POST", "/api/upload");
            this.body = body;
            addHeader("Transfer-Encoding", "chunked");
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new DelegatingServletInputStream(new InputStream() {
                @Override
                public int read() {
                    int value = source.read();
                    if (value >= 0) {
                        bytesRead++;
                    }
                    return value;
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    int count = source.read(b, off, Math.min(len, 256));
                    if (count > 0) {
                        bytesRead += count;
                    }
                    return count;
                }
            });
        }
    }

    private static MockHttpServletRequest withContentLength(int size) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
        request.setContent(new byte[size]);
        return request;
    }

    private static byte[] bodyOf(int size) {
        byte[] body = new byte[size];
        Arrays.fill(body, (byte) 'a');
        return body;
    }

    @Test
    @DisplayName("body with Content-Length exactly at the limit passes through")
    void contentLengthAtLimitPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(withContentLength(LIMIT), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("body with Content-Length one byte over the limit is rejected with 413 without reading it")
    void contentLengthOverLimitRejected() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(withContentLength(LIMIT + 1), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(written).containsExactly(CommonProblemTypes.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("chunked body exactly at the limit passes through and remains readable")
    void chunkedAtLimitPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new ChunkedRequest(bodyOf(LIMIT)), new MockHttpServletResponse(), chain);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
        assertThat(passed).isNotNull();
        assertThat(passed.getContentLengthLong()).isEqualTo(LIMIT);
        assertThat(passed.getContentLength()).isEqualTo(LIMIT);
        assertThat(passed.getInputStream().readAllBytes()).hasSize(LIMIT);
        assertThat(passed.getReader().readLine()).hasSize(LIMIT);
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("chunked body far over the limit is rejected and reading stops right after the limit")
    void chunkedOverLimitRejected() throws Exception {
        ChunkedRequest request = new ChunkedRequest(bodyOf(LIMIT * 4));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(written).containsExactly(CommonProblemTypes.PAYLOAD_TOO_LARGE);
        assertThat(request.bytesRead).isEqualTo(LIMIT + 1);
    }

    @Test
    @DisplayName("chunked body of the limit plus one byte is rejected")
    void chunkedLimitPlusOneRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new ChunkedRequest(bodyOf(LIMIT + 1)), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    @DisplayName("request without a body passes through untouched")
    void noBodyPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(written).isEmpty();
    }
}
