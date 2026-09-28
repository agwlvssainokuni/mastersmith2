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
package cherry.mastersmith.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.common.web.MastersmithWebProperties;
import cherry.mastersmith.invitation.repository.InvitationRepository;
import cherry.mastersmith.mail.service.MailSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.unit.DataSize;

/** 招待の定期の削除の単体テスト（BR11.1、NFR9.10）。 */
class InvitationCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-12-31T03:45:00Z");

    private final InvitationRepository repository = mock(InvitationRepository.class);

    private InvitationCleanupJob job;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        InvitationSettings settings = new InvitationSettings(
                new InvitationProperties(
                        Duration.ofHours(24), Duration.ofDays(90), new InvitationProperties.Cleanup("-")),
                new MastersmithWebProperties(null, false, DataSize.ofMegabytes(1)),
                mock(MailSender.class));
        job = new InvitationCleanupJob(repository, settings, Clock.fixed(NOW, ZoneOffset.UTC), manager);
    }

    @Test
    @DisplayName("the cutoff is now minus the retention for both kinds of rows")
    void cutoff() {
        job.run();

        verify(repository).deleteEndedBefore(NOW.minus(Duration.ofDays(90)), 1000);
        verify(repository).deleteExpiredPendingBefore(NOW.minus(Duration.ofDays(90)), 1000);
    }

    @Test
    @DisplayName("a full batch repeats until a batch is below the limit and the total is logged as INFO")
    void repeatsFullBatches() {
        when(repository.deleteEndedBefore(any(), anyInt())).thenReturn(1000, 1000, 3);
        when(repository.deleteExpiredPendingBefore(any(), anyInt())).thenReturn(1000, 0);

        try (LogEvents events = LogEvents.capture(InvitationCleanupJob.class)) {
            assertThat(job.run()).isEqualTo(3003);

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key + "=" + pair.value)
                        .containsExactly("deleted=3003", "ended=2003", "expiredPending=1000");
            });
        }
        verify(repository, times(3)).deleteEndedBefore(any(), eq(1000));
        verify(repository, times(2)).deleteExpiredPendingBefore(any(), eq(1000));
    }

    @Test
    @DisplayName("a batch exactly below the limit stops after one round")
    void stopsBelowLimit() {
        when(repository.deleteEndedBefore(any(), anyInt())).thenReturn(999);

        assertThat(job.run()).isEqualTo(999);
        verify(repository, times(1)).deleteEndedBefore(any(), anyInt());
    }

    @Test
    @DisplayName("a failure is logged as ERROR with the stack trace, returns -1 and is not thrown")
    void failure() {
        when(repository.deleteEndedBefore(any(), anyInt())).thenThrow(new IllegalStateException("テスト用の失敗"));

        try (LogEvents events = LogEvents.capture(InvitationCleanupJob.class)) {
            assertThat(job.run()).isEqualTo(-1);

            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
    }
}
