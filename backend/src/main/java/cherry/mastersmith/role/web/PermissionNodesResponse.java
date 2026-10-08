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

import java.util.List;

/**
 * 権限の設定の木の1階層の応答（契約 C7 の PermissionNodes）。
 *
 * @param items 節（DSL の順、今の DSL に無い節はその後）
 */
public record PermissionNodesResponse(List<PermissionNodeResponse> items) {

    /** 節を写して持つ。 */
    public PermissionNodesResponse {
        items = List.copyOf(items);
    }
}
