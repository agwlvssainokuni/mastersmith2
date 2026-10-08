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

/**
 * 1つの対象の明示の値の組（BR4.1・BR4.2）。どの値も null は「設定なし」。
 *
 * @param main 主権限（null は設定なし）
 * @param create 補助権限 CREATE（null は設定なし、true は可、false は不可）
 * @param delete 補助権限 DELETE（同上）
 */
public record PermissionValues(MainPermission main, Boolean create, Boolean delete) {

    /** すべて設定なし。 */
    public static final PermissionValues NOT_SET = new PermissionValues(null, null, null);

    /**
     * すべての値が設定なしかを返す（行を持たない状態。BR4.2）。
     *
     * @return すべて設定なしなら true
     */
    public boolean isEmpty() {
        return main == null && create == null && delete == null;
    }

    /**
     * 補助権限が設定されているかを返す（カラムでは持てない。BR4.1）。
     *
     * @return CREATE か DELETE のどちらかが設定されていれば true
     */
    public boolean hasAuxiliary() {
        return create != null || delete != null;
    }

    /**
     * null を設定なしに読み替える。
     *
     * @param values 値の組（null は行が無い）
     * @return 値の組
     */
    public static PermissionValues orNotSet(PermissionValues values) {
        return values == null ? NOT_SET : values;
    }
}
