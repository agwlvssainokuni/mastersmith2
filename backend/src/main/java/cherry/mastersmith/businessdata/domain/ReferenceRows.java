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

/**
 * 参照先の検索 API の成功の結果（Intent 261029-reference-lookup-cleanup の U1、契約 C2、entities.md の ReferenceRows）。
 *
 * <p>文字列にするときは行の数・ページ・次の有無だけを出す（BR10.1）。
 *
 * @param rows 行（検索は 0〜20、ラベルの1件読みはちょうど1）
 * @param page 検索のページの番号（ラベルの1件読みでは null）
 * @param hasNext 21 行目を読んだら true（ラベルの1件読みでは false。BR5.7）
 */
public record ReferenceRows(List<ReferenceRow> rows, Long page, boolean hasNext) {

    /** 一覧を変更できないものにし、行の数と組を確かめる。 */
    public ReferenceRows {
        rows = List.copyOf(rows);
        if (rows.size() > ReferenceRowsQuery.PAGE_SIZE) {
            throw new IllegalArgumentException("行は 20 までです");
        }
        if (page == null && (rows.size() != 1 || hasNext)) {
            throw new IllegalArgumentException("ラベルの1件読みはちょうど1行で、次はありません");
        }
    }

    /**
     * 検索の結果を作る。
     *
     * @param rows 行（0〜20）
     * @param page ページの番号
     * @param hasNext 次のページがあるか
     * @return 結果
     */
    public static ReferenceRows search(List<ReferenceRow> rows, long page, boolean hasNext) {
        return new ReferenceRows(rows, page, hasNext);
    }

    /**
     * ラベルの1件読みの結果を作る（{@code page} は null、次は無い。BR6.3）。
     *
     * @param row 行（セルは空）
     * @return 結果
     */
    public static ReferenceRows label(ReferenceRow row) {
        return new ReferenceRows(List.of(row), null, false);
    }

    /** 行の数・ページ・次の有無だけを出す。 */
    @Override
    public String toString() {
        return "ReferenceRows[rows=" + rows.size() + ", page=" + page + ", hasNext=" + hasNext + "]";
    }
}
