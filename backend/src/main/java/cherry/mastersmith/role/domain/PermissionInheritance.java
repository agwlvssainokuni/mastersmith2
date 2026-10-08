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
 * 継承の解決（{@code logical-components.md} の L6、BR5.1・BR5.2・BR5.6・BR4.11）。DB にも時計にも触れない純粋な関数である。
 *
 * <ul>
 *   <li>主権限は column ?? table ?? schema ?? NONE（BR5.1）
 *   <li>補助権限は table ?? schema ?? false を CREATE と DELETE で別々に求める。カラムの補助権限はそのテーブルの値（BR5.2）
 *   <li>継承の元は、値を決めた階層（求める対象の階層なら EXPLICIT、上位なら TABLE・SCHEMA、どこも設定なしなら DEFAULT）を主権限・
 *       CREATE・DELETE で別々に求める（BR4.11）
 * </ul>
 *
 * <p>下位の階層の明示の値が上位を上書きする。求める対象より下位の値は使わない（スキーマの対象にテーブルの値を渡しても使わない）。
 */
public final class PermissionInheritance {

    private PermissionInheritance() {}

    /**
     * 解決の結果。
     *
     * @param effective 実効の値
     * @param mainFrom 主権限の継承の元
     * @param createFrom CREATE の継承の元
     * @param deleteFrom DELETE の継承の元
     */
    public record Resolution(
            EffectivePermission effective, InheritedFrom mainFrom, InheritedFrom createFrom, InheritedFrom deleteFrom) {

        /** 値が null でないことを確かめる。 */
        public Resolution {
            Objects.requireNonNull(effective, "effective");
            Objects.requireNonNull(mainFrom, "mainFrom");
            Objects.requireNonNull(createFrom, "createFrom");
            Objects.requireNonNull(deleteFrom, "deleteFrom");
        }
    }

    /**
     * 対象の実効の値と継承の元を求める。
     *
     * @param level 求める対象の階層
     * @param schema スキーマの明示の値（行が無ければ null か {@link PermissionValues#NOT_SET}）
     * @param table テーブルの明示の値（同上。スキーマの対象では使わない）
     * @param column カラムの明示の値（同上。カラムの対象だけで使う）
     * @return 解決の結果
     */
    public static Resolution resolve(
            PermissionLevel level, PermissionValues schema, PermissionValues table, PermissionValues column) {
        Objects.requireNonNull(level, "level");
        PermissionValues s = PermissionValues.orNotSet(schema);
        PermissionValues t =
                level == PermissionLevel.SCHEMA ? PermissionValues.NOT_SET : PermissionValues.orNotSet(table);
        PermissionValues c =
                level == PermissionLevel.COLUMN ? PermissionValues.orNotSet(column) : PermissionValues.NOT_SET;
        InheritedFrom ownFrom = InheritedFrom.EXPLICIT;
        InheritedFrom tableFrom = level == PermissionLevel.TABLE ? ownFrom : InheritedFrom.TABLE;
        InheritedFrom schemaFrom = level == PermissionLevel.SCHEMA ? ownFrom : InheritedFrom.SCHEMA;

        MainPermission main = MainPermission.NONE;
        InheritedFrom mainFrom = InheritedFrom.DEFAULT;
        if (c.main() != null) {
            main = c.main();
            mainFrom = ownFrom;
        } else if (t.main() != null) {
            main = t.main();
            mainFrom = tableFrom;
        } else if (s.main() != null) {
            main = s.main();
            mainFrom = schemaFrom;
        }
        Auxiliary create = auxiliary(t.create(), s.create(), tableFrom, schemaFrom);
        Auxiliary delete = auxiliary(t.delete(), s.delete(), tableFrom, schemaFrom);
        return new Resolution(
                new EffectivePermission(main, create.value(), delete.value()), mainFrom, create.from(), delete.from());
    }

    /**
     * 主権限だけを求める（column ?? table ?? schema ?? NONE。BR5.1）。
     *
     * @param schema スキーマの主権限（null は設定なし）
     * @param table テーブルの主権限（同上）
     * @param column カラムの主権限（同上）
     * @return 実効の主権限
     */
    public static MainPermission main(MainPermission schema, MainPermission table, MainPermission column) {
        if (column != null) {
            return column;
        }
        if (table != null) {
            return table;
        }
        return schema != null ? schema : MainPermission.NONE;
    }

    /**
     * 補助権限を1つ求める（table ?? schema ?? false。BR5.2）。
     *
     * @param schema スキーマの値（null は設定なし）
     * @param table テーブルの値（同上）
     * @return 実効の値
     */
    public static boolean auxiliary(Boolean schema, Boolean table) {
        if (table != null) {
            return table;
        }
        return schema != null && schema;
    }

    private record Auxiliary(boolean value, InheritedFrom from) {}

    private static Auxiliary auxiliary(
            Boolean table, Boolean schema, InheritedFrom tableFrom, InheritedFrom schemaFrom) {
        if (table != null) {
            return new Auxiliary(table, tableFrom);
        }
        if (schema != null) {
            return new Auxiliary(schema, schemaFrom);
        }
        return new Auxiliary(false, InheritedFrom.DEFAULT);
    }
}
