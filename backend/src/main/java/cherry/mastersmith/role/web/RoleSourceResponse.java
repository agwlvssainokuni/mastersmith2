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
package cherry.mastersmith.role.web;

import cherry.mastersmith.role.domain.RoleSource;

/**
 * ロールの出どころの応答（契約 C7 の {@code DIRECT | GROUP(groupId, name)}、BR6.5）。
 *
 * @param type 出どころの種類（{@code DIRECT}・{@code GROUP}）
 * @param groupId グループの ID（{@code DIRECT} では null）
 * @param name グループの名前（{@code DIRECT} では null）
 */
public record RoleSourceResponse(String type, Long groupId, String name) {

    static RoleSourceResponse from(RoleSource source) {
        return switch (source) {
            case RoleSource.Direct _ -> new RoleSourceResponse("DIRECT", null, null);
            case RoleSource.Group(long groupId, String groupName) ->
                new RoleSourceResponse("GROUP", groupId, groupName);
        };
    }
}
