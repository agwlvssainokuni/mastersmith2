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
package cherry.mastersmith.group.service;

/** グループの行の排他の結果（{@code entities.md} の GroupRowLock、BR5.3）。 */
public sealed interface GroupRowLock permits GroupRowLock.Locked, GroupRowLock.Busy {

    /**
     * 排他を取れた（行が無ければ {@code exists} が偽で、何も排他していない）。
     *
     * @param exists グループがいるか
     */
    record Locked(boolean exists) implements GroupRowLock {}

    /** 待ちの上限切れ（呼び出し元は巻き戻して {@code GROUP_BUSY} で断る）。 */
    record Busy() implements GroupRowLock {}
}
