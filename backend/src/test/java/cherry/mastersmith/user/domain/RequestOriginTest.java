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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 要求の送り手の情報（security-design 2節）の単体テスト。 */
class RequestOriginTest {

    @Test
    @DisplayName("a 513-character User-Agent is cut to 512 characters and a 512-character one is kept")
    void userAgentIsCut() {
        assertThat(new RequestOrigin("192.0.2.1", "u".repeat(513), null).userAgent())
                .hasSize(512);
        assertThat(new RequestOrigin("192.0.2.1", "u".repeat(512), null).userAgent())
                .hasSize(512);
    }

    @Test
    @DisplayName("the User-Agent and the trace id may be absent")
    void optionalValues() {
        RequestOrigin origin = new RequestOrigin("192.0.2.1", null, null);

        assertThat(origin.userAgent()).isNull();
        assertThat(origin.traceId()).isNull();
    }

    @Test
    @DisplayName("the source IP is required")
    void sourceIpIsRequired() {
        assertThatThrownBy(() -> new RequestOrigin(null, "UA", "trace")).isInstanceOf(NullPointerException.class);
    }
}
