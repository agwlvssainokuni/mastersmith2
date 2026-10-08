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

import java.util.Objects;

/**
 * グループの要約（契約 C4 の {@code summaries}、BR6.4）。
 *
 * @param groupId グループの ID
 * @param name 名前
 */
public record GroupSummary(long groupId, String name) {

    /** 名前が null でないことを確かめる。 */
    public GroupSummary {
        Objects.requireNonNull(name, "name");
    }
}
