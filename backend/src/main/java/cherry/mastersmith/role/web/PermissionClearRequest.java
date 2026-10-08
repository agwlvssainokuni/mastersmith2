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
 * 今の DSL に無い設定を消す要求の本文（契約 C7 の {@code POST .../permissions/clear}、BR4.7）。
 *
 * @param targets 消す対象
 */
public record PermissionClearRequest(List<PermissionTargetRequest> targets) {}
