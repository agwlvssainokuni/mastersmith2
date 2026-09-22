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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CacheControlFilterTest {

    private final CacheControlFilter filter = new CacheControlFilter();

    private String cacheControl(String contextPath, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(contextPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
        return response.getHeader("Cache-Control");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("API and Actuator responses are not stored")
    @ValueSource(strings = {"/api/problems/not-found", "/api", "/actuator/health", "/actuator"})
    void apiAndActuatorNoStore(String uri) throws Exception {
        assertThat(cacheControl("", uri)).isEqualTo("no-store");
    }

    @Test
    @DisplayName("hashed screen files are cached for a year as immutable")
    void assetsImmutable() throws Exception {
        assertThat(cacheControl("", "/assets/index-abc123.js")).isEqualTo("public, max-age=31536000, immutable");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("entry page and screen URLs must be revalidated")
    @ValueSource(strings = {"/", "/index.html", "/admin/users", "/apiary", "/assets"})
    void screenNoCache(String uri) throws Exception {
        assertThat(cacheControl("", uri)).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("context path is removed before matching")
    void contextPathRemoved() throws Exception {
        assertThat(cacheControl("/app", "/app/api/x")).isEqualTo("no-store");
        assertThat(cacheControl("/app", "/app/assets/a.css")).isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("filter runs before the Spring Security filter chain and also for error dispatches")
    void runsBeforeSecurity() {
        assertThat(filter.getOrder()).isLessThan(-100);
        assertThat(filter.shouldNotFilterErrorDispatch()).isFalse();
    }
}
