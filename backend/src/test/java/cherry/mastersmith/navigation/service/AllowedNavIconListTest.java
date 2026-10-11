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
package cherry.mastersmith.navigation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import cherry.mastersmith.navigation.service.AllowedNavIconList.Failure;
import cherry.mastersmith.navigation.service.AllowedNavIconList.InvalidIconListException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/** アイコンの一覧のファイルの読み方と誤りのテスト（BR4.1・BR4.3、NFR6.8、計画の 13節 Q6: A）。 */
class AllowedNavIconListTest {

    private static Failure failureOf(Runnable action) {
        try {
            action.run();
        } catch (InvalidIconListException e) {
            return e.failure();
        }
        throw new AssertionError("一覧のファイルの誤りになりませんでした");
    }

    @Test
    @DisplayName("comment lines and blank lines are skipped and surrounding spaces are removed")
    void parsesNames() {
        String content = "# header\n\n  list  \r\nhome\n\t# indented comment\n   \nuser\t\n";

        assertThat(AllowedNavIconList.parse(content)).containsExactly("list", "home", "user");
    }

    @Test
    @DisplayName("a file without names, without list or with duplicates is rejected")
    void rejectsInvalidContents() {
        assertThat(failureOf(() -> AllowedNavIconList.parse("# only comments\n\n")))
                .isEqualTo(Failure.EMPTY);
        assertThat(failureOf(() -> AllowedNavIconList.parse(""))).isEqualTo(Failure.EMPTY);
        assertThat(failureOf(() -> AllowedNavIconList.parse("home\nuser\n"))).isEqualTo(Failure.NO_DEFAULT);
        assertThat(failureOf(() -> AllowedNavIconList.parse("List\nhome\n"))).isEqualTo(Failure.NO_DEFAULT);
        assertThat(failureOf(() -> AllowedNavIconList.parse("list\nhome\n home \n")))
                .isEqualTo(Failure.DUPLICATE);
    }

    @Test
    @DisplayName("a missing file stops the start-up with only the reason in the error log")
    void missingFile() {
        try (LogEvents events = LogEvents.capture(AllowedNavIconList.class)) {
            assertThatThrownBy(() -> AllowedNavIconList.load(new ClassPathResource("navigation/no-such-icons.txt")))
                    .isInstanceOfSatisfying(
                            InvalidIconListException.class,
                            e -> assertThat(e.failure()).isEqualTo(Failure.MISSING))
                    .hasMessageNotContaining("no-such-icons");
            ILoggingEvent event = events.list().getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getKeyValuePairs())
                    .singleElement()
                    .satisfies(pair -> assertThat(pair.value).isEqualTo(Failure.MISSING));
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    @Test
    @DisplayName("an unreadable file stops the start-up")
    void unreadableFile() {
        Resource unreadable = new AbstractResource() {
            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public String getDescription() {
                return "unreadable";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("読めない");
            }
        };

        assertThat(failureOf(() -> AllowedNavIconList.load(unreadable))).isEqualTo(Failure.UNREADABLE);
    }

    @Test
    @DisplayName("a file given as a resource is read in UTF-8")
    void loadsResource() {
        Resource resource = new ByteArrayResource("# ヘッダー\nlist\nhome\n".getBytes(StandardCharsets.UTF_8));

        assertThat(AllowedNavIconList.load(resource)).containsExactly("list", "home");
    }

    @Test
    @DisplayName("the bundled file skips its license header and holds the 18 names of make-you-chic-ui")
    void bundledFile() {
        assertThat(new AllowedNavIconList().policy().allowed())
                .containsExactlyInAnyOrder(
                        "menu",
                        "chevron-down",
                        "chevron-up",
                        "close",
                        "check",
                        "bell",
                        "user",
                        "search",
                        "edit",
                        "trash",
                        "download",
                        "settings",
                        "home",
                        "list",
                        "info",
                        "success",
                        "warning",
                        "danger");
    }
}
