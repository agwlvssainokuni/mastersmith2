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
package cherry.mastersmith.navigation.testsupport;

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.domain.DslModel;
import cherry.mastersmith.dsl.service.ActiveDslModelProvider;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 呼ばれた回数を数える DSL の提供口の替え物（{@code logical-components.md} の T3、NFR2.3）。Spring を起動しない単体テストで
 * {@code NavigationService} に直に渡す。
 */
public final class CountingDslProvider implements ActiveDslModelProvider {

    private final AtomicInteger calls = new AtomicInteger();

    private volatile DslModel model;

    /**
     * 作る。
     *
     * @param model 返すモデル（null は「無い」）
     */
    public CountingDslProvider(DslModel model) {
        this.model = model;
    }

    /**
     * 返すモデルを差し替える。
     *
     * @param model 返すモデル（null は「無い」）
     */
    public void replace(DslModel model) {
        this.model = model;
    }

    @Override
    public ActiveDsl current() {
        calls.incrementAndGet();
        return ActiveDsl.of(model);
    }

    /**
     * 呼ばれた回数を返す。
     *
     * @return 回数
     */
    public int calls() {
        return calls.get();
    }
}
