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

import java.util.Optional;

/** 主権限（{@code entities.md} の MainPermission、BR4.1）。「設定なし」は値が無いこと（null）で表す。 */
public enum MainPermission {
    /** 見られない。 */
    NONE,
    /** 見られる・直せない。 */
    READ,
    /** 見られる・直せる。 */
    FULL;

    /**
     * 名前から主権限を読む（大文字と小文字を区別する。許していない値は空）。
     *
     * @param raw 名前（null でもよい）
     * @return 主権限（null・許していない値は空）
     */
    public static Optional<MainPermission> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (MainPermission value : values()) {
            if (value.name().equals(raw)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
