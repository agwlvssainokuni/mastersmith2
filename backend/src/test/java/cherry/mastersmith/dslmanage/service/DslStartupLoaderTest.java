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

import static cherry.mastersmith.dslmanage.testsupport.DslYaml.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.service.ActiveDslModelStore;
import cherry.mastersmith.dsl.service.DslReader;
import cherry.mastersmith.dsl.validate.PatternChecker;
import cherry.mastersmith.dslmanage.domain.DslAppliedRef;
import cherry.mastersmith.dslmanage.domain.DslContent;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.repository.DslAppliedRevisionRepository;
import cherry.mastersmith.dslmanage.repository.DslPreviewRepository;
import cherry.mastersmith.dslmanage.testsupport.DslYaml;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 起動時の読み込み（BR5.1・BR5.2、NFR8.4）の単体テスト。 */
class DslStartupLoaderTest {

    private static final PatternChecker PATTERNS = new PatternChecker();

    private static final DslReader READER = DslYaml.newReader(PATTERNS);

    private final DslAppliedRevisionRepository revisions = mock(DslAppliedRevisionRepository.class);

    private final ActiveDslModelStore active = new ActiveDslModelStore();

    private final DslStartupLoader loader =
            new DslStartupLoader(new DslRecordStore(mock(DslPreviewRepository.class), revisions), READER, active);

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    private void stored(byte[] body) {
        DslAppliedRef ref = new DslAppliedRef(
                UUID.randomUUID(), READER.hash(body), DslSource.UPLOAD, 1L, Instant.parse("2026-09-24T00:00:00Z"));
        when(revisions.findCurrentContent()).thenReturn(Optional.of(new DslContent<>(ref, body)));
    }

    @Test
    @DisplayName("without history the applied DSL stays absent")
    void noHistory() {
        loader.afterSingletonsInstantiated();

        assertThat(active.current()).isInstanceOf(ActiveDsl.Absent.class);
    }

    @Test
    @DisplayName("the latest revision becomes the applied model")
    void latestRevisionIsLoaded() {
        byte[] body = DslYaml.dsl().table("t", column("c")).bytes();
        stored(body);

        loader.afterSingletonsInstantiated();

        assertThat(active.current())
                .isInstanceOfSatisfying(
                        ActiveDsl.Present.class,
                        present -> assertThat(present.dslHash()).isEqualTo(READER.hash(body)));
    }

    @Test
    @DisplayName("an unreadable revision logs one error with the hash and error kinds only and starts without a DSL")
    void unreadableRevision() {
        byte[] body = "version: 2\nsecret_body_text: 1\n".getBytes(StandardCharsets.UTF_8);
        stored(body);

        try (LogEvents logs = LogEvents.capture(DslStartupLoader.class)) {
            loader.afterSingletonsInstantiated();

            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage()).isEqualTo(DslStartupLoader.INVALID_MESSAGE);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(String.valueOf(event.getKeyValuePairs()))
                        .contains("UNSUPPORTED_VERSION")
                        .contains(READER.hash(body).substring(0, 12))
                        .doesNotContain("secret_body_text");
            });
        }
        assertThat(active.current()).isInstanceOf(ActiveDsl.Absent.class);
    }
}
