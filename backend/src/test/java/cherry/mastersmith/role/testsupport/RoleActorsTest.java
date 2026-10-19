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
package cherry.mastersmith.role.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * テストの主体（{@link Actor}）の文字列の伏せ字の単体テスト（Intent 261016-business-data-views の U5、{@code security-design.md}
 * S4.2）。
 */
class RoleActorsTest {

    private static final String EMAIL = "role-admin-sample@example.com";

    private static final String TOKEN = "sample.access.token-value";

    @Test
    @DisplayName("toString hides the email and the token and keeps the user id")
    void toStringHidesSecrets() {
        Actor actor = new Actor(123L, EMAIL, TOKEN);

        String text = actor.toString();

        assertThat(text).contains("userId=123").doesNotContain(EMAIL).doesNotContain("example.com");
        assertThat(text).doesNotContain(TOKEN).doesNotContain("sample.access");
        assertThat(text).isEqualTo("Actor[userId=123, email=***, token=***]");
    }

    @Test
    @DisplayName("email and token accessors still return the values as they are")
    void accessorsReturnValues() {
        Actor actor = new Actor(7L, EMAIL, TOKEN);

        assertThat(actor.userId()).isEqualTo(7L);
        assertThat(actor.email()).isEqualTo(EMAIL);
        assertThat(actor.token()).isEqualTo(TOKEN);
        assertThat(new Actor(7L, EMAIL, null).token()).isNull();
    }
}
