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
 * 作業ロールの参照（契約 C5 の WorkRoleRef、{@code entities.md}）。ロールの ID と名前だけを持つ（個人に関する値を持たない）。
 *
 * @param roleId ロールの ID
 * @param name ロールの名前
 */
public record WorkRoleRef(long roleId, String name) {

    /** 名前が null でないことを確かめる。 */
    public WorkRoleRef {
        Objects.requireNonNull(name, "name");
    }
}
