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

import cherry.mastersmith.dsl.domain.DslModel;

/**
 * 適用中のモデルの差し替えの口（契約 C4 の {@code ActiveDslModelHolder}）。呼ぶのは U4 だけ（起動時と、適用の確定の後）。
 *
 * <p>差し替えは、読み手（{@link ActiveDslModelProvider}）から見て一度に切り替わる（BR5.3）。
 */
public interface ActiveDslModelHolder {

    /**
     * 適用中のモデルを差し替える。
     *
     * @param model 新しい適用中のモデル（null は「適用中の DSL が無い」）
     */
    void replace(DslModel model);
}
