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
package cherry.mastersmith.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DummyPasswordHashTest {

    private static LongSupplier elapsed(long millis) {
        Iterator<Long> values = List.of(0L, millis * 1_000_000L).iterator();
        return values::next;
    }

    private static ILoggingEvent create(long millis) {
        try (LogEvents events = LogEvents.capture(DummyPasswordHash.class)) {
            new DummyPasswordHash(new BCryptPasswordEncoder(4), new PasswordProperties(4), elapsed(millis));
            return events.list().getFirst();
        }
    }

    @Test
    @DisplayName("a check time within 100 to 500 milliseconds is logged at INFO with cost and time")
    void withinRange() {
        ILoggingEvent event = create(250);

        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getKeyValuePairs().toString()).contains("bcryptCost").contains("elapsedMs");
        assertThat(create(100).getLevel()).isEqualTo(Level.INFO);
        assertThat(create(500).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("a check time outside the range is logged at WARN")
    void outOfRange() {
        assertThat(create(99).getLevel()).isEqualTo(Level.WARN);
        assertThat(create(501).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("the dummy hash is a bcrypt hash with the configured cost and hides itself in toString")
    void configuredCost() {
        DummyPasswordHash dummy = new DummyPasswordHash(new BCryptPasswordEncoder(4), new PasswordProperties(4));

        assertThat(dummy.hash()).startsWith("$2a$04$");
        assertThat(new BCryptPasswordEncoder(4).matches(dummy.substituteInput(), dummy.hash()))
                .isTrue();
        assertThat(dummy.toString()).doesNotContain(dummy.hash());
    }
}
