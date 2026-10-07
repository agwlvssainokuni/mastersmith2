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
package cherry.mastersmith.dslmanage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 起動時に読めなかった適用中の版の ID の置き場の単体テスト（U2 dsl-v2 の BR3.6、NFR 設計の 4.8）。 */
class UnreadableAppliedRevisionTest {

    private final UnreadableAppliedRevision revision = new UnreadableAppliedRevision();

    @Test
    @DisplayName("nothing is unreadable before anything is remembered")
    void emptyAtFirst() {
        assertThat(revision.isUnreadable(UUID.randomUUID())).isFalse();
        assertThat(revision.isUnreadable(null)).isFalse();
    }

    @Test
    @DisplayName("only the remembered id is unreadable")
    void onlyTheRememberedId() {
        UUID id = UUID.randomUUID();
        revision.remember(id);

        assertThat(revision.isUnreadable(id)).isTrue();
        assertThat(revision.isUnreadable(UUID.fromString(id.toString()))).isTrue();
        assertThat(revision.isUnreadable(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("a null current revision is never unreadable")
    void nullCurrentRevision() {
        revision.remember(UUID.randomUUID());

        assertThat(revision.isUnreadable(null)).isFalse();
    }

    @Test
    @DisplayName("after clear nothing is unreadable, and remembering again replaces the id")
    void clearAndReplace() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        revision.remember(first);
        revision.clear();

        assertThat(revision.isUnreadable(first)).isFalse();

        revision.remember(first);
        revision.remember(second);
        assertThat(revision.isUnreadable(first)).isFalse();
        assertThat(revision.isUnreadable(second)).isTrue();
    }
}
