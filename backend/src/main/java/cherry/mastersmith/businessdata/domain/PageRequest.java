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

import java.util.Optional;
import java.util.Set;

/**
 * 一覧の1ページの指定（entities.md の PageRequest、BR5.1・BR5.7、NFR 設計の Q4: A）。
 *
 * @param page ページの番号（1〜{@value #MAX_PAGE}）
 * @param size 1ページの件数（20・50・100）
 * @param sort 要求の並べ替え（無ければ null）
 */
public record PageRequest(long page, int size, SortKey sort) {

    /** ページの番号の上限（NFR4.5、Q4: A）。 */
    public static final long MAX_PAGE = 1_000_000L;

    /** 1ページの件数の既定。 */
    public static final int DEFAULT_SIZE = 20;

    /** 受け付ける1ページの件数。 */
    public static final Set<Integer> SIZES = Set.of(20, 50, 100);

    /** 範囲を確かめる。 */
    public PageRequest {
        if (page < 1 || page > MAX_PAGE) {
            throw new IllegalArgumentException("page は 1〜" + MAX_PAGE + " です");
        }
        if (!SIZES.contains(size)) {
            throw new IllegalArgumentException("size は 20・50・100 です");
        }
    }

    /**
     * 読み飛ばす行の数を、桁あふれしない整数で返す（BR5.7。最大は 99,999,900）。
     *
     * @return 読み飛ばす行の数
     */
    public long offset() {
        return (page - 1) * size;
    }

    /**
     * 要求の並べ替えを返す。
     *
     * @return 並べ替え（無ければ空）
     */
    public Optional<SortKey> sortKey() {
        return Optional.ofNullable(sort);
    }
}
