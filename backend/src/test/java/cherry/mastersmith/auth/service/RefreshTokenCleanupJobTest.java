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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.auth.repository.RefreshTokenRepository;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RefreshTokenCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-09-22T18:30:00Z");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

    private RefreshTokenCleanupJob job;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        job = new RefreshTokenCleanupJob(
                repository,
                SigningKeyProviderTest.properties("unused"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    @Test
    @DisplayName("deleted rows are counted and logged at INFO with the cutoff seven days before now")
    void logsCount() {
        when(repository.deleteExpiredBefore(NOW.minus(Duration.ofDays(7)), 1000))
                .thenReturn(3);

        try (LogEvents events = LogEvents.capture(RefreshTokenCleanupJob.class)) {
            assertThat(job.run()).isEqualTo(3);
            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getKeyValuePairs().toString()).contains("deleted=\"3\"");
            });
        }
    }

    @Test
    @DisplayName("deletion repeats while a full batch of one thousand rows was deleted")
    void repeatsBatches() {
        when(repository.deleteExpiredBefore(any(), anyInt())).thenReturn(1000, 1000, 5);

        assertThat(job.run()).isEqualTo(2005);
        verify(repository, times(3)).deleteExpiredBefore(any(), anyInt());
    }

    @Test
    @DisplayName("a failure is logged at ERROR with the stack trace and not thrown")
    void failureLogged() {
        when(repository.deleteExpiredBefore(any(), anyInt())).thenThrow(new IllegalStateException("DB が使えない"));

        try (LogEvents events = LogEvents.capture(RefreshTokenCleanupJob.class)) {
            assertThat(job.run()).isEqualTo(-1);
            assertThat(events.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
    }
}
