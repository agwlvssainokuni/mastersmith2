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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.AllowedOperation;
import cherry.mastersmith.businessdata.domain.ColumnAccess;
import cherry.mastersmith.businessdata.domain.DefinitionPurpose;
import cherry.mastersmith.businessdata.domain.ReferenceField;
import cherry.mastersmith.businessdata.domain.RestrictionReason;
import cherry.mastersmith.businessdata.domain.TableAccessView;
import cherry.mastersmith.dsl.domain.DisplayName;
import java.util.List;
import java.util.Map;

/**
 * 定義の API の応答（契約 C2 の {@code TableDefinition} に、機能設計の X3 の {@code rowLabelColumns} と BR9.1 の
 * {@code definitionChanged} を足した形）。名前と設定だけを持ち、行の値を持たない。
 *
 * @param definitionId 今の定義の識別
 * @param definitionChanged 要求の識別が今と違うか
 * @param schema スキーマ名
 * @param table テーブル名
 * @param displayName テーブルの表示名
 * @param view ビューか
 * @param hasPrimaryKey 主キー（ビューは主キーの設定）があるか
 * @param primaryKeyColumns 行を指す主キーのカラム（DETAIL が許されるときだけ。ほかは空）
 * @param operations 許す操作
 * @param restrictionReason 行えない操作の理由（無ければ null）
 * @param writeConfigured 書き込みの接続の設定があるか（U1 では常に false）
 * @param rowLabelColumns 行を見分ける値のカラム
 * @param listColumns 一覧の列
 * @param detailColumns 詳細の列
 * @param searchFields 検索の項目
 * @param sortableColumns 並べ替えに使えるカラム
 * @param defaultSort 既定の並び
 */
public record TableDefinitionResponse(
        DefinitionIdBody definitionId,
        boolean definitionChanged,
        String schema,
        String table,
        DisplayName displayName,
        boolean view,
        boolean hasPrimaryKey,
        List<String> primaryKeyColumns,
        List<AllowedOperation> operations,
        RestrictionReason restrictionReason,
        boolean writeConfigured,
        List<String> rowLabelColumns,
        List<ColumnDefinitionResponse> listColumns,
        List<ColumnDefinitionResponse> detailColumns,
        List<SearchFieldResponse> searchFields,
        List<String> sortableColumns,
        List<SortKeyBody> defaultSort) {

    /**
     * 判定の材料から作る。
     *
     * @param view 判定の材料
     * @param definitionChanged 要求の識別が今と違うか
     * @return 応答
     */
    static TableDefinitionResponse from(TableAccessView view, boolean definitionChanged) {
        return from(view, definitionChanged, DefinitionPurpose.VIEW, Map.of());
    }

    /**
     * 判定の材料と参照の項目から作る（Intent 261029-reference-lookup-cleanup の U1。各カラムに {@code reference} を載せる）。
     *
     * @param view 判定の材料
     * @param definitionChanged 要求の識別が今と違うか
     * @param purpose 用途
     * @param references カラムの名前ごとの参照の項目（VIEW では空）
     * @return 応答
     */
    static TableDefinitionResponse from(
            TableAccessView view,
            boolean definitionChanged,
            DefinitionPurpose purpose,
            Map<String, ReferenceField> references) {
        return new TableDefinitionResponse(
                DefinitionIdBody.from(view.definitionId()),
                definitionChanged,
                view.schema(),
                view.table(),
                view.displayName(),
                view.view(),
                !view.primaryKey().isEmpty(),
                view.keyColumns().stream().map(ColumnAccess::column).toList(),
                List.copyOf(view.operations()),
                view.restrictionReason(),
                view.writeConfigured(),
                view.rowLabelColumns(),
                view.listColumns().stream()
                        .map(c -> ColumnDefinitionResponse.from(c, purpose, references.get(c.column())))
                        .toList(),
                view.detailColumns().stream()
                        .map(c -> ColumnDefinitionResponse.from(c, purpose, references.get(c.column())))
                        .toList(),
                view.searchColumns().stream().map(SearchFieldResponse::from).toList(),
                view.columns().stream()
                        .filter(ColumnAccess::sortable)
                        .map(ColumnAccess::column)
                        .toList(),
                view.defaultSortKeys().stream()
                        .map(k -> new SortKeyBody(k.column(), k.direction().name()))
                        .toList());
    }

    /** テーブルの名前と数だけを出す。 */
    @Override
    public String toString() {
        return "TableDefinitionResponse[schema=" + schema + ", table=" + table + ", listColumns=" + listColumns.size()
                + ", operations=" + operations + "]";
    }
}
