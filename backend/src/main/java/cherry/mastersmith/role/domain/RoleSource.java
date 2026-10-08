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
package cherry.mastersmith.role.domain;

import java.util.Objects;

/**
 * 利用者のロールの出どころ（{@code entities.md} の RoleSource、BR6.5）。直接の割り当てか、所属するグループへの割り当てか。
 */
public sealed interface RoleSource permits RoleSource.Direct, RoleSource.Group {

    /** 利用者への直接の割り当て。 */
    record Direct() implements RoleSource {}

    /**
     * 所属するグループへの割り当て。
     *
     * @param groupId グループの ID
     * @param groupName グループの名前
     */
    record Group(long groupId, String groupName) implements RoleSource {

        /** 名前が null でないことを確かめる。 */
        public Group {
            Objects.requireNonNull(groupName, "groupName");
        }
    }
}
