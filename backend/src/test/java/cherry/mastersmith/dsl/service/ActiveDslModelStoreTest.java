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
package cherry.mastersmith.dsl.service;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.testsupport.DslSamples;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 適用中のモデルの保持と提供口の単体テスト（BR5.3・BR5.4、契約 C4・C8）。 */
class ActiveDslModelStoreTest {

    private static final DslModel M1 = DefaultDslReaderTest.model(DslSamples.validYaml());

    private static final DslModel M2 = DefaultDslReaderTest.model(DslSamples.validYaml() + "# second\n");

    @Test
    @DisplayName("the store goes from absent to M1 to M2 and back to absent")
    void replaceSequence() {
        ActiveDslModelStore store = new ActiveDslModelStore();
        assertThat(M1.dslHash()).isNotEqualTo(M2.dslHash());

        assertThat(store.current()).isInstanceOf(ActiveDsl.Absent.class);
        store.replace(M1);
        assertThat(store.current()).isEqualTo(new ActiveDsl.Present(M1, M1.dslHash()));
        store.replace(M2);
        assertThat(store.current()).isEqualTo(new ActiveDsl.Present(M2, M2.dslHash()));
        store.replace(null);
        assertThat(store.current()).isInstanceOf(ActiveDsl.Absent.class);
    }

    @Test
    @DisplayName("a model taken out is not affected by a later replacement")
    void takenModelIsStable() {
        ActiveDslModelStore store = new ActiveDslModelStore();
        store.replace(M1);
        ActiveDsl taken = store.current();

        store.replace(M2);

        assertThat(taken).isEqualTo(new ActiveDsl.Present(M1, M1.dslHash()));
        assertThat(((ActiveDsl.Present) taken).model().tables()).isEqualTo(M1.tables());
    }

    @Test
    @DisplayName("readers running during replacements only ever see the old or the new model, never a mix")
    void replacementIsAtomicForReaders() throws Exception {
        ActiveDslModelStore store = new ActiveDslModelStore();
        store.replace(M1);
        int readers = 4;
        int rounds = 20_000;
        ExecutorService executor = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<List<String>>> results = new ArrayList<>();
            for (int r = 0; r < readers; r++) {
                results.add(executor.submit(() -> {
                    start.await();
                    List<String> problems = new ArrayList<>();
                    for (int i = 0; i < rounds; i++) {
                        if (!(store.current() instanceof ActiveDsl.Present present)
                                || !(present.model() == M1 || present.model() == M2)
                                || !present.dslHash().equals(present.model().dslHash())) {
                            problems.add("round " + i);
                        }
                    }
                    return problems;
                }));
            }
            Future<?> writer = executor.submit(() -> {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    store.replace(i % 2 == 0 ? M2 : M1);
                }
                return null;
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            for (Future<List<String>> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEmpty();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
