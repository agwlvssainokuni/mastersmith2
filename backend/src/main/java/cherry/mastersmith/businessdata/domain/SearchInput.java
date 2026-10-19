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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一覧と検索の本文のうち、条件・並べ替え・ページ（確かめる前。BR4・BR5.1・BR5.2）。
 *
 * <p>ページの番号と件数は、整数でない値も見分けられるよう数のまま受ける。文字列にするときは条件の数だけを出す（BR10.1）。
 *
 * @param conditions 条件（無ければ空）
 * @param sort 並べ替え（無ければ null）
 * @param page ページの番号（無ければ null で、1 として扱う）
 * @param size 1ページの件数（無ければ null で、20 として扱う）
 */
public record SearchInput(List<RawCondition> conditions, RawSort sort, BigDecimal page, BigDecimal size) {

    /** 条件の一覧を変更できないものにする（null の要素は確かめで弾く）。 */
    public SearchInput {
        conditions = conditions == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(conditions));
    }

    /** 条件の数・並べ替えの有無・ページと件数だけを出す（値を出さない）。 */
    @Override
    public String toString() {
        return "SearchInput[conditions=" + conditions.size() + ", sort=" + (sort != null) + ", page=" + page + ", size="
                + size + "]";
    }
}
