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
package cherry.mastersmith;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.common.testsupport.PublicApiInventory;
import cherry.mastersmith.common.testsupport.PublicApiInventory.Diff;
import cherry.mastersmith.common.testsupport.PublicApiInventory.PublicApiEntry;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * 検査の検査: PUBLIC の口の一覧の比べ（{@link PublicApiInventory}）が増減を本当に落とすことの確かめ（security-design 4.4.1、
 * 要件 NFR1.6 の境界）。Spring を起動しない。
 */
class PublicApiInventoryTest {

    private static final PublicApiEntry PROBLEM = PublicApiEntry.of("/api/problems/{slug}", "GET");

    private static final PublicApiEntry ERROR_READ = PublicApiEntry.of("/error", "GET", "HEAD", "OPTIONS");

    private static Set<PublicApiEntry> replace(PublicApiEntry from, PublicApiEntry to) {
        Set<PublicApiEntry> copy = new HashSet<>(PublicApiInventory.EXPECTED);
        assertThat(copy.remove(from)).as("見本の元の行が一覧にある").isTrue();
        copy.add(to);
        return copy;
    }

    @Test
    @DisplayName("the inventory holds the nine PUBLIC endpoints counted per handler method")
    void holdsNineEntries() {
        assertThat(PublicApiInventory.EXPECTED).hasSize(9);
        assertThat(PublicApiInventory.EXPECTED.stream()
                        .mapToInt(entry ->
                                entry.methods().size() * entry.patterns().size())
                        .sum())
                .as("方法と道の組では 14")
                .isEqualTo(14);
    }

    @Test
    @DisplayName("an unchanged set of PUBLIC endpoints shows no difference")
    void unchangedShowsNoDifference() {
        Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, Set.copyOf(PublicApiInventory.EXPECTED));

        assertThat(diff.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a PUBLIC endpoint missing from the inventory shows up as added")
    void addedEndpointIsReported() {
        PublicApiEntry extra = PublicApiEntry.of("/api/sample", "POST");
        Set<PublicApiEntry> actual = new HashSet<>(PublicApiInventory.EXPECTED);
        actual.add(extra);

        Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, actual);

        assertThat(diff.added()).containsExactly(extra);
        assertThat(diff.removed()).isEmpty();
    }

    @Test
    @DisplayName("an inventory row whose endpoint is no longer PUBLIC shows up as removed")
    void removedEndpointIsReported() {
        PublicApiEntry appearance = PublicApiEntry.of("/api/appearance", "GET");
        Set<PublicApiEntry> actual = new HashSet<>(PublicApiInventory.EXPECTED);
        actual.remove(appearance);

        Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, actual);

        assertThat(diff.added()).isEmpty();
        assertThat(diff.removed()).containsExactly(appearance);
    }

    @Test
    @DisplayName("renaming a path variable shows one added and one removed row")
    void renamedPathVariableIsReported() {
        PublicApiEntry renamed = PublicApiEntry.of("/api/problems/{name}", "GET");

        Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, replace(PROBLEM, renamed));

        assertThat(diff.added()).containsExactly(renamed);
        assertThat(diff.removed()).containsExactly(PROBLEM);
    }

    @Test
    @DisplayName("adding a declared method to /error shows one added and one removed row")
    void addedErrorMethodIsReported() {
        PublicApiEntry withTrace = PublicApiEntry.of("/error", "GET", "HEAD", "OPTIONS", "TRACE");

        Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, replace(ERROR_READ, withTrace));

        assertThat(diff.added()).containsExactly(withTrace);
        assertThat(diff.removed()).containsExactly(ERROR_READ);
    }

    @Test
    @DisplayName("removing a declared method from /error shows one added and one removed row")
    void removedErrorMethodIsReported() {
        PublicApiEntry withoutHead = PublicApiEntry.of("/error", "GET", "OPTIONS");

        Diff diff = PublicApiInventory.diff(PublicApiInventory.EXPECTED, replace(ERROR_READ, withoutHead));

        assertThat(diff.added()).containsExactly(withoutHead);
        assertThat(diff.removed()).containsExactly(ERROR_READ);
    }

    @Test
    @DisplayName("entryOf keeps only the declared methods and the variable names of the path pattern")
    void entryOfKeepsDeclaredMethodsAndVariableNames() {
        RequestMappingInfo info = RequestMappingInfo.paths("/api/problems/{slug}")
                .methods(RequestMethod.GET)
                .build();

        assertThat(PublicApiInventory.entryOf(info)).isEqualTo(PROBLEM);
    }

    @Test
    @DisplayName("entryOf treats a mapping without declared methods as an empty method set, not as every method")
    void entryOfWithoutMethods() {
        RequestMappingInfo info = RequestMappingInfo.paths("/api/sample").build();

        PublicApiEntry entry = PublicApiInventory.entryOf(info);

        assertThat(entry.methods()).isEmpty();
        assertThat(entry.patterns()).containsExactly("/api/sample");
        assertThat(entry).isNotEqualTo(PublicApiEntry.of("/api/sample", "GET", "POST", "PUT", "PATCH", "DELETE"));
    }
}
