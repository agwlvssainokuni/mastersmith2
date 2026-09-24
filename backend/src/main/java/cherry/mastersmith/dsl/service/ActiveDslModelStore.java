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

import cherry.mastersmith.dsl.domain.ActiveDsl;
import cherry.mastersmith.dsl.domain.DslModel;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 適用中のモデルの保持（{@link ActiveDslModelHolder}・{@link ActiveDslModelProvider}。BR5.3・BR5.4）。
 *
 * <p>{@link AtomicReference} で結果を1つ持ち、差し替えは参照の置き換え1回で行う。起動の直後は「無い」から始まる。モデルは変更
 * できない値の木のため、取り出した後の差し替えの影響を受けない。
 */
@Component
public class ActiveDslModelStore implements ActiveDslModelHolder, ActiveDslModelProvider {

    private final AtomicReference<ActiveDsl> current = new AtomicReference<>(ActiveDsl.of(null));

    @Override
    public void replace(DslModel model) {
        current.set(ActiveDsl.of(model));
    }

    @Override
    public ActiveDsl current() {
        return current.get();
    }
}
