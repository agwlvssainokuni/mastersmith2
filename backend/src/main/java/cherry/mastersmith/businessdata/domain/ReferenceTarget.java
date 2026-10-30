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

import cherry.mastersmith.dsl.domain.LookupListItem;
import cherry.mastersmith.dsl.domain.LookupSearchItem;
import cherry.mastersmith.dsl.domain.OptionSource;
import cherry.mastersmith.dsl.domain.OptionSourceKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 参照元のカラムの DSL の options（REFERENCE・LOOKUP）から引いた参照先（Intent 261029-reference-lookup-cleanup の U1、契約 C1、
 * entities.md の ReferenceTarget）。利用者の入力からは作らず、要求1件の権限の写しの DSL から読み出しのたびに作る（BR2.1〜BR2.3）。
 * 参照先の形（複合の主キー・主キーの無いテーブル・ビュー・自己参照）では作ることを拒まない（BR2.4）。
 *
 * <p>文字列にするときは kind とテーブルの名前だけを出す（BR10.1）。
 *
 * @param kind 種類（{@code REFERENCE} か {@code LOOKUP}）
 * @param schema 参照先のスキーマ（参照元と同じ）
 * @param table 参照先のテーブル（DSL の {@code options.table}）
 * @param valueColumn 値のカラム（DSL の {@code options.valueColumn}）
 * @param labelColumn ラベルに使うカラム（BR2.5）
 * @param lookupSearch LOOKUP の検索の条件（DSL の並び。REFERENCE では空）
 * @param lookupList LOOKUP の一覧の列（{@code order} の昇順、同じ順は DSL の並び。REFERENCE では空）
 */
public record ReferenceTarget(
        OptionSourceKind kind,
        String schema,
        String table,
        String valueColumn,
        String labelColumn,
        List<LookupSearchItem> lookupSearch,
        List<LookupListItem> lookupList) {

    /** 必須の値を確かめ、一覧を変更できないものにする。 */
    public ReferenceTarget {
        Objects.requireNonNull(kind, "kind は必須です");
        if (kind == OptionSourceKind.FIXED) {
            throw new IllegalArgumentException("参照先は REFERENCE か LOOKUP です");
        }
        Objects.requireNonNull(schema, "schema は必須です");
        Objects.requireNonNull(table, "table は必須です");
        Objects.requireNonNull(valueColumn, "valueColumn は必須です");
        Objects.requireNonNull(labelColumn, "labelColumn は必須です");
        lookupSearch = List.copyOf(lookupSearch);
        lookupList = List.copyOf(lookupList);
    }

    /**
     * 参照元のカラムの DSL の options から参照先を作る（BR2.1・BR2.5）。
     *
     * @param schema 参照元のスキーマ（写しの DSL と照らした後の名前）
     * @param options 参照元のカラムの DSL の options（無ければ null）
     * @return 参照先（REFERENCE・LOOKUP でなければ空）
     */
    public static Optional<ReferenceTarget> from(String schema, OptionSource options) {
        if (options == null || options.source() == OptionSourceKind.FIXED) {
            return Optional.empty();
        }
        List<LookupListItem> ordered = new ArrayList<>(options.lookupList());
        // List.sort は安定のため、同じ order は DSL の並びのまま（BR2.5・BR5.1）。
        ordered.sort(Comparator.comparingInt(LookupListItem::order));
        String label = options.labelColumn() != null
                ? options.labelColumn()
                : ordered.stream().findFirst().map(LookupListItem::column).orElse(null);
        if (label == null) {
            // DSL の検証で起きない（REFERENCE は labelColumn が必須、LOOKUP は lookupList が1件以上）。
            return Optional.empty();
        }
        return Optional.of(new ReferenceTarget(
                options.source(),
                schema,
                options.table(),
                options.valueColumn(),
                label,
                options.source() == OptionSourceKind.LOOKUP ? options.lookupSearch() : List.of(),
                options.source() == OptionSourceKind.LOOKUP ? ordered : List.of()));
    }

    /** kind とテーブルの名前だけを出す（BR10.1）。 */
    @Override
    public String toString() {
        return "ReferenceTarget[kind=" + kind + ", table=" + table + "]";
    }
}
