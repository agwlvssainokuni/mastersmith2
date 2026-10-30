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
package cherry.mastersmith.businessdata.repository;

import cherry.mastersmith.businessdata.domain.RowQueryFailure;
import cherry.mastersmith.businessdata.domain.RowRecord;
import java.util.List;
import java.util.Objects;

/** 一覧と検索の読み取りの結果（件数とページ、または失敗）。 */
public sealed interface RowSearchResult {

    /**
     * 読めた。
     *
     * @param total 条件に合う全体の件数
     * @param rows ページの行（最後のページを超えたら空）
     */
    record Page(long total, List<RowRecord> rows) implements RowSearchResult {

        /** 一覧を変更できないものにする。 */
        public Page {
            rows = List.copyOf(rows);
        }
    }

    /**
     * 件数を数えずに読めた（参照先の読み取り。Intent 261029-reference-lookup-cleanup の U1、BR11.4）。行の主キーの側に値のカラムの値
     * （切らない）を、値の側に表示の列（一覧の上限で切る）を持つ。
     *
     * @param rows 読んだ行（読む行の上限まで）
     */
    record Rows(List<RowRecord> rows) implements RowSearchResult {

        /** 一覧を変更できないものにする。 */
        public Rows {
            rows = List.copyOf(rows);
        }
    }

    /**
     * 失敗した。
     *
     * @param failure 失敗
     */
    record Failed(RowQueryFailure failure) implements RowSearchResult {

        /** 失敗が必須であることを確かめる。 */
        public Failed {
            Objects.requireNonNull(failure, "failure は必須です");
        }
    }
}
