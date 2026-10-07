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

    private final UnreadableAppliedRevision unreadable = new UnreadableAppliedRevision();

    private final DslStartupLoader loader = new DslStartupLoader(
            new DslRecordStore(mock(DslPreviewRepository.class), revisions), READER, active, unreadable);

    @AfterAll
    static void close() {
        PATTERNS.close();
    }

    private UUID stored(byte[] body) {
        DslAppliedRef ref = new DslAppliedRef(
                UUID.randomUUID(), READER.hash(body), DslSource.UPLOAD, 1L, Instant.parse("2026-09-24T00:00:00Z"));
        when(revisions.findCurrentContent()).thenReturn(Optional.of(new DslContent<>(ref, body)));
        return ref.revisionId();
    }

    @Test
    @DisplayName("without history the applied DSL stays absent")
    void noHistory() {
        UUID earlier = UUID.randomUUID();
        unreadable.remember(earlier);

        loader.afterSingletonsInstantiated();

        assertThat(active.current()).isInstanceOf(ActiveDsl.Absent.class);
        assertThat(unreadable.isUnreadable(earlier))
                .as("cleared when nothing is applied")
                .isFalse();
    }

    @Test
    @DisplayName("the latest revision becomes the applied model")
    void latestRevisionIsLoaded() {
        byte[] body = DslYaml.dsl().table("t", column("c")).bytes();
        UUID id = stored(body);
        unreadable.remember(id);

        try (LogEvents logs = LogEvents.capture(DslStartupLoader.class)) {
            loader.afterSingletonsInstantiated();

            assertThat(logs.list()).isEmpty();
        }
        assertThat(active.current())
                .isInstanceOfSatisfying(
                        ActiveDsl.Present.class,
                        present -> assertThat(present.dslHash()).isEqualTo(READER.hash(body)));
        assertThat(unreadable.isUnreadable(id)).as("cleared when read").isFalse();
    }

    @Test
    @DisplayName("an unreadable revision logs one error with the hash and error kinds only and starts without a DSL")
    void unreadableRevision() {
        byte[] body = "version: 1\nsecret_body_text: 1\n".getBytes(StandardCharsets.UTF_8);
        UUID id = stored(body);

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
        assertThat(unreadable.isUnreadable(id))
                .as("the unreadable revision is remembered")
                .isTrue();
        assertThat(unreadable.isUnreadable(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName(
            "a too deep menu is pruned and one warning with only the hash, the pruned count and the limit is logged")
    void tooDeepMenuIsPrunedWithOneWarning() {
        byte[] body = DslYaml.dsl()
                .table("secret_table_name", column("c"))
                .menu("見えるメニュー", "Visible", "secret_table_name")
                .deepMenu(7, "secret_table_name")
                .bytes();
        UUID id = stored(body);

        try (LogEvents logs = LogEvents.capture(DslStartupLoader.class)) {
            loader.afterSingletonsInstantiated();

            assertThat(logs.list()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage()).isEqualTo(DslStartupLoader.PRUNED_MESSAGE);
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.key)
                        .containsExactly("dsl.hash", "dsl.prunedMenuItems", "dsl.menuDepthLimit");
                assertThat(event.getKeyValuePairs())
                        .extracting(pair -> pair.value)
                        .containsExactly(READER.hash(body).substring(0, 12), 7, 5);
                assertThat(String.valueOf(event.getKeyValuePairs()) + event.getFormattedMessage())
                        .doesNotContain("secret_table_name", "段", "level", "見えるメニュー");
            });
        }
        assertThat(active.current()).isInstanceOfSatisfying(ActiveDsl.Present.class, present -> {
            assertThat(present.dslHash()).isEqualTo(READER.hash(body));
            assertThat(present.model().menus())
                    .singleElement()
                    .satisfies(item -> assertThat(item.label().ja()).isEqualTo("見えるメニュー"));
        });
        assertThat(unreadable.isUnreadable(id)).isFalse();
    }
}
