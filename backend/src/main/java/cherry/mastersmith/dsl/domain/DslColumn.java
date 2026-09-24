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
package cherry.mastersmith.dsl.domain;

import java.util.List;
import java.util.Objects;

/**
 * カラム1つの定義。
 *
 * @param name 物理名
 * @param label 表示名
 * @param dbType DB 上の型
 * @param formPart フォーム部品
 * @param search 検索条件
 * @param list 一覧表示
 * @param detail 詳細・編集の画面に出すか
 * @param validations バリデーション（0件以上。DSL の順）
 * @param options 選択肢の出どころ（無ければ null）
 */
public record DslColumn(
        String name,
        DisplayName label,
        DbType dbType,
        FormPart formPart,
        SearchSetting search,
        ListSetting list,
        DetailSetting detail,
        List<Validation> validations,
        OptionSource options) {

    /** 必須の値を確かめ、バリデーションを変更できない一覧にする。 */
    public DslColumn {
        ModelValues.requireName(name, "column.name");
        Objects.requireNonNull(label, "column.label は必須です");
        Objects.requireNonNull(dbType, "column.dbType は必須です");
        Objects.requireNonNull(formPart, "column.formPart は必須です");
        Objects.requireNonNull(search, "column.search は必須です");
        Objects.requireNonNull(list, "column.list は必須です");
        Objects.requireNonNull(detail, "column.detail は必須です");
        validations = ModelValues.copyList(validations, "column.validations");
    }
}
