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
 * ロールの ID・名前・名前の鍵の読み取りの形（権限の YAML の照らし合わせ。BR9.6）。
 *
 * @param roleId ロールの ID
 * @param name 名前
 * @param nameKey 名前の鍵
 */
public record RoleKeyView(long roleId, String name, String nameKey) {

    /** 値が null でないことを確かめる。 */
    public RoleKeyView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(nameKey, "nameKey");
    }

    /** ID だけを出す（権限の YAML の照らし合わせで TRACE に出るため、名前を出さない。NFR1.9）。 */
    @Override
    public String toString() {
        return "RoleKeyView[roleId=" + roleId + "]";
    }
}
