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
 * 実効の権限（{@code entities.md} の EffectivePermission、契約 C5）。カラムの補助権限は、そのカラムのテーブルの実効の値。
 *
 * @param main 主権限
 * @param create 補助権限 CREATE
 * @param delete 補助権限 DELETE
 */
public record EffectivePermission(MainPermission main, boolean create, boolean delete) {

    /** 権限なし（NONE・不可。BR5.3）。 */
    public static final EffectivePermission NONE = new EffectivePermission(MainPermission.NONE, false, false);

    /** 主権限が null でないことを確かめる。 */
    public EffectivePermission {
        Objects.requireNonNull(main, "main");
    }
}
