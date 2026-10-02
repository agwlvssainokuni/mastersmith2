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
package cherry.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 利用者の管理に向けたロックの判定の口（U3、BR1.7）の単体テスト。 */
class LockAdministrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private final LoginAttemptStateRepository repository = mock(LoginAttemptStateRepository.class);

    private final LockAdministrationService service =
            new LockAdministrationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("each requested user gets a view judged at the clock's time, in the requested order")
    void judgesWithClock() {
        when(repository.findBySubjectIds(List.of(3L, 1L, 2L)))
                .thenReturn(List.of(
                        new LoginAttemptState(1L, 5, NOW.plusSeconds(60)),
                        new LoginAttemptState(2L, 5, NOW),
                        new LoginAttemptState(3L, 2, null)));

        Map<Long, LockView> views = service.lockViewsOf(List.of(3L, 1L, 2L));

        assertThat(views.keySet()).containsExactly(3L, 1L, 2L);
        assertThat(views.get(1L)).isEqualTo(new LockView(true, NOW.plusSeconds(60), true));
        assertThat(views.get(2L)).isEqualTo(new LockView(false, null, true));
        assertThat(views.get(3L)).isEqualTo(new LockView(false, null, true));
    }

    @Test
    @DisplayName("a user without a row gets the empty view")
    void missingRow() {
        when(repository.findBySubjectIds(List.of(9L))).thenReturn(List.of());

        assertThat(service.lockViewsOf(List.of(9L))).containsExactly(Map.entry(9L, LockView.NONE));
    }

    @Test
    @DisplayName("an empty id list issues no query")
    void emptyIds() {
        assertThat(service.lockViewsOf(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("duplicate ids are queried once and non-positive ids are rejected")
    void duplicatesAndDummyIds() {
        when(repository.findBySubjectIds(List.of(4L))).thenReturn(List.of());

        assertThat(service.lockViewsOf(List.of(4L, 4L))).containsOnlyKeys(4L);
        verify(repository).findBySubjectIds(List.of(4L));
        assertThatThrownBy(() -> service.lockViewsOf(List.of(4L, -1L))).isInstanceOf(IllegalArgumentException.class);
    }
}
