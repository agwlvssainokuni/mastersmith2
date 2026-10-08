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
 * 一覧の1行の射影（ロールの ID と名前。BR3.5）。
 *
 * @param roleId ロールの ID
 * @param name 名前
 */
public record RoleRowView(long roleId, String name) {

    /** 名前が null でないことを確かめる。 */
    public RoleRowView {
        Objects.requireNonNull(name, "name");
    }
}
