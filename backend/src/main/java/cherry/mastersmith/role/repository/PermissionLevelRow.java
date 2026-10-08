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

import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionValues;
import java.util.Objects;

/**
 * 木の1階層の1つの子の射影（BR4.10）。子の名前ごとに、その子の階層の明示の値と、その子の下（その子の行を含む）の設定の行の数を
 * 1回の集計で読む。
 *
 * @param name 子の名前（テーブルの階層の読み取りでは、空の文字列の行がスキーマの行）
 * @param main その子の階層の主権限（行が無ければ null）
 * @param create その子の階層の CREATE（同上）
 * @param delete その子の階層の DELETE（同上）
 * @param rowsInSubtree その子と、その下の設定の行の数
 */
public record PermissionLevelRow(String name, MainPermission main, Boolean create, Boolean delete, long rowsInSubtree) {

    /** 名前が null でないことを確かめる。 */
    public PermissionLevelRow {
        Objects.requireNonNull(name, "name");
    }

    /**
     * その子の階層の明示の値を返す。
     *
     * @return 値の組（行が無ければすべて設定なし）
     */
    public PermissionValues values() {
        return new PermissionValues(main, create, delete);
    }

    /**
     * その子より下の階層の設定の行の数を返す（その子の行は少なくとも1つの値を持つため、値があれば1行を引く）。
     *
     * @return 行の数
     */
    public long deeperRows() {
        return rowsInSubtree - (values().isEmpty() ? 0 : 1);
    }
}
