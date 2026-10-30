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

import java.util.List;
import java.util.Objects;

/**
 * 参照先の検索 API の要求を、本文の形の確かめ（{@link ReferenceRowsRequestValidator}、BR1.4）の後に読んだもの（Intent
 * 261029-reference-lookup-cleanup の U1、entities.md の ReferenceRowsQuery）。検索（{@link Mode#SEARCH}）とラベルの1件読み
 * （{@link Mode#VALUE}）のどちらか一方。名前はまだ DSL と照らしていない（照らすまで SQL に使わない）。
 *
 * <p>文字列にするときは名前・種類・条件の数・ページだけを出し、条件の値と値を伏せる（BR10.1）。
 *
 * @param schema 参照元のスキーマ（照らす前）
 * @param table 参照元のテーブル（照らす前）
 * @param column 参照元のカラム（照らす前）
 * @param mode 検索かラベルの1件読みか
 * @param conditions 検索の条件（SEARCH のとき1件以上。値が空の条件を含む。VALUE では空）
 * @param page ページの番号（SEARCH のとき 1〜{@value PageRequest#MAX_PAGE}。VALUE では null）
 * @param value ラベルを引く値（VALUE のときだけ。SEARCH では null）
 */
public record ReferenceRowsQuery(
        String schema,
        String table,
        String column,
        Mode mode,
        List<ReferenceCondition> conditions,
        Long page,
        ReferenceValue value) {

    /** 検索の1ページの行の数（BR5.7）。 */
    public static final int PAGE_SIZE = 20;

    /** 要求の種類。 */
    public enum Mode {
        /** ピッカーの検索（conditions と page）。 */
        SEARCH,
        /** ラベルの1件読み（value）。 */
        VALUE
    }

    /** 必須の値と、種類ごとに持つものの組を確かめる。 */
    public ReferenceRowsQuery {
        Objects.requireNonNull(schema, "schema は必須です");
        Objects.requireNonNull(table, "table は必須です");
        Objects.requireNonNull(column, "column は必須です");
        Objects.requireNonNull(mode, "mode は必須です");
        conditions = List.copyOf(conditions);
        boolean ok =
                switch (mode) {
                    case SEARCH ->
                        !conditions.isEmpty()
                                && conditions.stream().anyMatch(c -> !c.isEmpty())
                                && page != null
                                && page >= 1
                                && page <= PageRequest.MAX_PAGE
                                && value == null;
                    case VALUE -> conditions.isEmpty() && page == null && value != null;
                };
        if (!ok) {
            throw new IllegalArgumentException("検索とラベルの1件読みの組が食い違います");
        }
    }

    /**
     * 検索の要求を作る。
     *
     * @param schema 参照元のスキーマ
     * @param table 参照元のテーブル
     * @param column 参照元のカラム
     * @param conditions 条件
     * @param page ページの番号
     * @return 要求
     */
    public static ReferenceRowsQuery search(
            String schema, String table, String column, List<ReferenceCondition> conditions, long page) {
        return new ReferenceRowsQuery(schema, table, column, Mode.SEARCH, conditions, page, null);
    }

    /**
     * ラベルの1件読みの要求を作る。
     *
     * @param schema 参照元のスキーマ
     * @param table 参照元のテーブル
     * @param column 参照元のカラム
     * @param value 値
     * @return 要求
     */
    public static ReferenceRowsQuery label(String schema, String table, String column, ReferenceValue value) {
        return new ReferenceRowsQuery(schema, table, column, Mode.VALUE, List.of(), null, value);
    }

    /**
     * 検索で読み飛ばす行の数（{@code (page-1)×20}。最大 19,999,980 で int に収まる。BR5.7）。
     *
     * @return 読み飛ばす行の数（VALUE では 0）
     */
    public int offset() {
        return page == null ? 0 : Math.toIntExact((page - 1) * PAGE_SIZE);
    }

    /** 名前・種類・条件の数・ページだけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "ReferenceRowsQuery[schema=" + schema + ", table=" + table + ", column=" + column + ", mode=" + mode
                + ", conditions=" + conditions.size() + ", page=" + page + ", value=" + (value == null ? "null" : "***")
                + "]";
    }
}
