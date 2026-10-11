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
package cherry.mastersmith.navigation.domain;

/** 業務のメニューが空の理由（{@code entities.md} の ENT-005、BR3.3）。 */
public enum EmptyReason {
    /** 適用済みの DSL が無い、または DSL の menus が空。 */
    NOT_CONFIGURED,
    /** DSL の menus はあるが、作業ロールが無い・すべて NONE などで絞った結果が空。 */
    NOTHING_VISIBLE
}
