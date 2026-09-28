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
package cherry.mastersmith.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * テストの JVM で、内部DB（H2 2.4.240）の閉じるときの全体の詰め直しのスレッドが 1 本に固定されていることの確かめ（Intent
 * 260925-user-management の U3 の直し）。
 *
 * <p>並べて行う（CPU の数 / 4 本）と H2 の中の競合に当たり、assert が有効なテストの JVM では詰め直しが中断されてファイルが縮まない
 * （{@code H2CompactionByPoolSuspensionIT} が CPU 8 の PC で落ちた）。本番は Dockerfile の ENTRYPOINT、E2E は
 * {@code frontend/playwright.config.ts} で同じ値を渡す。H2 の読み取り（{@code Integer.getInteger("h2.compactThreads", ...)}）と同じ
 * 方法で値を読む。
 */
class H2CompactThreadsTest {

    @Test
    @DisplayName("the test JVM runs H2 full compaction with exactly one thread")
    void compactThreadsIsOne() {
        assertThat(Integer.getInteger("h2.compactThreads")).isEqualTo(1);
    }
}
