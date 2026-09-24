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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.error.domain.LocalizedText;
import cherry.mastersmith.common.error.domain.ProblemType;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 本文の上限の道ごとの決まりと、断ったことの知らせ（Intent 260923-dsl-schema-loader の U4 の決定 A、NFR2.6）の単体テスト。 */
class RequestSizeLimitRoutesTest {

    private static final int DEFAULT_LIMIT = 1024;

    private static final int ROUTE_LIMIT = 4096;

    private static final ProblemType ROUTE_TOO_LARGE = new ProblemType(
            "ROUTE_TOO_LARGE",
            413,
            new LocalizedText("大きすぎます", "Too large"),
            new LocalizedText("上限を超えました。", "The limit was exceeded."),
            null);

    private static final RequestBodyLimitRoute ROUTE =
            new RequestBodyLimitRoute("post", "/api/admin/dsl/preview", ROUTE_LIMIT, ROUTE_TOO_LARGE);

    private final List<ProblemType> written = new ArrayList<>();

    private final List<RequestSizeRejection> notices = new ArrayList<>();

    private final ErrorResponseWriter writer = (request, response, type) -> {
        written.add(type);
        response.setStatus(type.status());
    };

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(
            DEFAULT_LIMIT,
            List.of(ROUTE),
            List.of((request, rejection) -> notices.add(rejection), (request, rejection) -> {
                throw new IllegalStateException("受け取り側の失敗");
            }),
            writer);

    private static MockHttpServletRequest request(String method, String path, int size) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContent(new byte[size]);
        return request;
    }

    private MockFilterChain run(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    @DisplayName("the route accepts a body exactly at its own limit, above the default limit")
    void routeLimitAtBoundary() throws Exception {
        MockFilterChain chain = run(request("POST", "/api/admin/dsl/preview", ROUTE_LIMIT));

        assertThat(chain.getRequest()).isNotNull();
        assertThat(written).isEmpty();
        assertThat(notices).isEmpty();
    }

    @Test
    @DisplayName("one byte over the route limit is rejected with the route code and the notice carries the route")
    void routeLimitExceeded() throws Exception {
        MockFilterChain chain = run(request("POST", "/api/admin/dsl/preview", ROUTE_LIMIT + 1));

        assertThat(chain.getRequest()).isNull();
        assertThat(written).containsExactly(ROUTE_TOO_LARGE);
        assertThat(notices).singleElement().satisfies(notice -> {
            assertThat(notice.route()).isEqualTo(ROUTE);
            assertThat(notice.maxBytes()).isEqualTo(ROUTE_LIMIT);
            assertThat(notice.contentLength()).isEqualTo(ROUTE_LIMIT + 1);
            assertThat(notice.path()).isEqualTo("/api/admin/dsl/preview");
        });
    }

    @Test
    @DisplayName("other methods and paths keep the default limit and code")
    void otherRoutesKeepTheDefault() throws Exception {
        run(request("PUT", "/api/admin/dsl/preview", DEFAULT_LIMIT + 1));
        run(request("POST", "/api/admin/dsl/apply", DEFAULT_LIMIT + 1));

        assertThat(written).containsExactly(CommonProblemTypes.PAYLOAD_TOO_LARGE, CommonProblemTypes.PAYLOAD_TOO_LARGE);
        assertThat(notices)
                .hasSize(2)
                .allSatisfy(notice -> assertThat(notice.route()).isNull());
    }

    @Test
    @DisplayName("the route matches the path inside the application without the context path")
    void contextPathIsIgnored() throws Exception {
        MockHttpServletRequest request = request("POST", "/app/api/admin/dsl/preview", ROUTE_LIMIT);
        request.setContextPath("/app");

        assertThat(run(request).getRequest()).isNotNull();
    }

    @Test
    @DisplayName("a route must have a positive limit and a 413 problem type")
    void routeValidation() {
        assertThatThrownBy(() -> new RequestBodyLimitRoute("POST", "/x", 0, ROUTE_TOO_LARGE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestBodyLimitRoute("POST", "/x", 1, CommonProblemTypes.NOT_FOUND))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
