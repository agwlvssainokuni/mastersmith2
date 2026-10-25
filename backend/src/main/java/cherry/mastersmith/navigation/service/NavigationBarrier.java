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

/**
 * 読み取りと DSL の適用し直しの重なりを作るための待ち合わせの口（{@code logical-components.md} の L7、{@code reliability-design.md} の
 * 2節、NFR1.10）。本番の実装は何もしない {@link NoOpNavigationBarrier} だけで、テストは {@code @Primary} の部品で点で止めて合図で
 * 再開する。
 */
public interface NavigationBarrier {

    /** メニュー: {@code current()} の後、{@code snapshotFor} の前。 */
    void afterDslRead();
}
