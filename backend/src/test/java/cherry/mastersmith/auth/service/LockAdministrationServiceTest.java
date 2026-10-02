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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cherry.mastersmith.auth.domain.LockView;
import cherry.mastersmith.auth.domain.LoginAttemptState;
import cherry.mastersmith.auth.repository.LoginAttemptStateRepository;
import cherry.mastersmith.common.persistence.RowLockAttempt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 利用者の管理に向けたロックの判定の口（U3、BR1.7）の単体テスト。 */
class LockAdministrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    private final LoginAttemptStateRepository repository = mock(LoginAttemptStateRepository.class);

    private final LoginAttemptBarrier barrier = mock(LoginAttemptBarrier.class);

    private final LockAdministrationService service =
            new LockAdministrationService(repository, Clock.fixed(NOW, ZoneOffset.UTC), barrier);

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

    // --- B4: 失敗回数を戻す2段の口（FS の D6、BR4.5、R-05）

    private void givenLockedRow(LoginAttemptState row) {
        when(repository.tryLockForUpdate(7L)).thenReturn(RowLockAttempt.acquired(Optional.ofNullable(row)));
    }

    @Test
    @DisplayName("the first step is Ready for failures of one or more, and also for zero failures with an unlock time")
    void prepareReady() {
        givenLockedRow(new LoginAttemptState(7L, 1, null));
        assertThat(service.prepareFailureReset(7L)).isEqualTo(new LoginFailureResetPreparation.Ready());

        givenLockedRow(new LoginAttemptState(7L, 5, NOW.minusSeconds(1)));
        assertThat(service.prepareFailureReset(7L)).isEqualTo(new LoginFailureResetPreparation.Ready());

        givenLockedRow(new LoginAttemptState(7L, 0, NOW.plusSeconds(60)));
        assertThat(service.prepareFailureReset(7L)).isEqualTo(new LoginFailureResetPreparation.Ready());
    }

    @Test
    @DisplayName("the first step is NothingToReset for a missing row and for zero failures without an unlock time")
    void prepareNothingToReset() {
        givenLockedRow(null);
        assertThat(service.prepareFailureReset(7L)).isEqualTo(new LoginFailureResetPreparation.NothingToReset());

        givenLockedRow(new LoginAttemptState(7L, 0, null));
        assertThat(service.prepareFailureReset(7L)).isEqualTo(new LoginFailureResetPreparation.NothingToReset());

        verify(repository, never()).update(anyLong(), anyInt(), any());
        verify(repository, never()).createIfAbsent(anyLong());
    }

    @Test
    @DisplayName("a lock failure of the first step is Busy and passes no waiting point")
    void prepareBusy() {
        when(repository.tryLockForUpdate(7L)).thenReturn(RowLockAttempt.busy());

        assertThat(service.prepareFailureReset(7L)).isEqualTo(new LoginFailureResetPreparation.Busy());
        verifyNoInteractions(barrier);
    }

    @Test
    @DisplayName("the waiting point is passed right after the row is locked, and not when there is no row")
    void barrierRightAfterLock() {
        givenLockedRow(new LoginAttemptState(7L, 2, null));
        service.prepareFailureReset(7L);
        InOrder order = inOrder(repository, barrier);
        order.verify(repository).tryLockForUpdate(7L);
        order.verify(barrier).afterLock(7L);

        givenLockedRow(null);
        service.prepareFailureReset(7L);
        verify(barrier, times(1)).afterLock(anyLong());
    }

    @Test
    @DisplayName("the second step writes zero and no unlock time with one explicit update and never creates a row")
    void complete() {
        when(repository.update(7L, 0, null)).thenReturn(1);

        service.completeFailureReset(7L);

        verify(repository).update(7L, 0, null);
        verify(repository, never()).createIfAbsent(anyLong());
    }

    @Test
    @DisplayName("a missing row in the second step and non-positive ids are unexpected errors")
    void unexpectedInputs() {
        when(repository.update(7L, 0, null)).thenReturn(0);

        assertThatThrownBy(() -> service.completeFailureReset(7L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.prepareFailureReset(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepareFailureReset(-1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.completeFailureReset(-1L)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).tryLockForUpdate(anyLong());
    }
}
