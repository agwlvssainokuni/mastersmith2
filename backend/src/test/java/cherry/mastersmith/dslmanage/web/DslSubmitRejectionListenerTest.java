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
package cherry.mastersmith.dslmanage.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import cherry.mastersmith.common.error.domain.CommonProblemTypes;
import cherry.mastersmith.common.web.RequestBodyLimitRoute;
import cherry.mastersmith.common.web.RequestSizeRejection;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.service.DslLifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

/** 大きさで断った投入の受け取り（決定 A、BR7.4）の単体テスト。 */
class DslSubmitRejectionListenerTest {

    private final DslLifecycle lifecycle = mock(DslLifecycle.class);

    private final DslSubmitRejectionListener listener =
            new DslSubmitRejectionListener(lifecycle, new DslRequestContextResolver(null));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the source is taken from the query string only for UPLOAD and PASTE")
    void sourceFromQuery() {
        assertThat(DslSubmitRejectionListener.source("source=UPLOAD")).isEqualTo(DslSource.UPLOAD);
        assertThat(DslSubmitRejectionListener.source("a=1&source=PASTE")).isEqualTo(DslSource.PASTE);
        assertThat(DslSubmitRejectionListener.source("source=RESTORE")).isNull();
        assertThat(DslSubmitRejectionListener.source(null)).isNull();
    }

    @Test
    @DisplayName("rejections of other routes and rejections without a logged-in user are not recorded")
    void ignoresOtherRejections() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", DslAdminPaths.PREVIEW);
        listener.onRejected(
                request, new RequestSizeRejection("POST", "/api/x", 1, 2, CommonProblemTypes.PAYLOAD_TOO_LARGE, null));
        RequestBodyLimitRoute route =
                new RequestBodyLimitRoute("POST", DslAdminPaths.PREVIEW, 10, DslProblemTypes.DSL_TOO_LARGE);
        listener.onRejected(
                request,
                new RequestSizeRejection("POST", DslAdminPaths.PREVIEW, 10, 11, DslProblemTypes.DSL_TOO_LARGE, route));

        verifyNoInteractions(lifecycle);
    }
}
