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
package cherry.mastersmith.role.repository;

import java.util.Objects;

/**
 * グループに割り当てられたロールの射影（グループの ID・ロールの ID・ロールの名前。BR6.5、計画の D-15）。
 *
 * @param groupId グループの ID
 * @param roleId ロールの ID
 * @param name ロールの名前
 */
public record GroupAssignedRoleRow(long groupId, long roleId, String name) {

    /** 名前が null でないことを確かめる。 */
    public GroupAssignedRoleRow {
        Objects.requireNonNull(name, "name");
    }
}
