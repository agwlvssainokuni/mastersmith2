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
package cherry.mastersmith.businessdata.domain;

import cherry.mastersmith.dsl.domain.DisplayName;
import cherry.mastersmith.dsl.domain.ListFormat;
import cherry.mastersmith.dsl.domain.SortDirection;
import cherry.mastersmith.role.domain.MainPermission;
import java.util.Objects;

/**
 * カラム1つの見せ方と入力の可否（契約 C1、entities.md の ColumnAccess、BR2）。主権限が NONE のカラムは作らない（BR2.1）。定義の
 * 応答のカラムの項目（契約 C2 の {@code ColumnDefinition}）の元になる（BR2.8）。
 *
 * @param column カラムの名前
 * @param displayName 表示名
 * @param main 実効の主権限（READ か FULL）
 * @param valueType 受け渡しの型（BR7.1）
 * @param dbTypeName DSL の型の名前（値を型として読むときの範囲の判定に使う）
 * @param primaryKey 主キーのカラムなら true
 * @param listVisible 一覧に出すなら true
 * @param listOrder 一覧の並び順（無ければ null）
 * @param width 一覧の幅（無ければ null）
 * @param listFormat 一覧の書式（無ければ null）
 * @param detailVisible 詳細に出すなら true
 * @param search 検索の決まり（検索の項目に出さないなら null。BR4.10）
 * @param searchDefault 検索の既定の値（DSL のまま。無ければ null。BR4.8）
 * @param searchCollapsed 検索の折りたたみの欄に置くなら true（BR4.8）
 * @param sortable 並べ替えに使えるなら true（BR5.2）
 * @param defaultSort 既定の並びの向き（無ければ null。BR5.3）
 * @param editableOnCreate 作成で入力できるなら true（BR2.4）
 * @param editableOnUpdate 更新で入力できるなら true（BR2.4）
 * @param masked フォーム部品が password なら true（値を返さない。BR7.4）
 * @param validation 入力の検証の材料（U2 が埋める。U1 は null）
 */
public record ColumnAccess(
        String column,
        DisplayName displayName,
        MainPermission main,
        ValueType valueType,
        String dbTypeName,
        boolean primaryKey,
        boolean listVisible,
        Integer listOrder,
        Integer width,
        ListFormat listFormat,
        boolean detailVisible,
        SearchRule search,
        String searchDefault,
        boolean searchCollapsed,
        boolean sortable,
        SortDirection defaultSort,
        boolean editableOnCreate,
        boolean editableOnUpdate,
        boolean masked,
        ColumnValidation validation) {

    /** 必須の値を確かめ、NONE のカラムを作らないことを確かめる。 */
    public ColumnAccess {
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(displayName, "displayName は必須です");
        Objects.requireNonNull(main, "main は必須です");
        Objects.requireNonNull(valueType, "valueType は必須です");
        Objects.requireNonNull(dbTypeName, "dbTypeName は必須です");
        if (main == MainPermission.NONE) {
            throw new IllegalArgumentException("NONE のカラムは作りません");
        }
    }

    /**
     * 値を対象DB から読むカラムか（password と BINARY は読まない。BR7.4・BR7.5）。
     *
     * @return 読むなら true
     */
    public boolean readsValue() {
        return !masked && valueType != ValueType.BINARY;
    }
}
