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

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * 参照先の検索 API の本文の形の確かめの性質ベースのテスト（jqwik。Intent 261029-reference-lookup-cleanup の U1、BR1.4、NFR1.14）。
 * 失敗のときの乱数の種は失敗の報告に残り、{@code @Property(seed = "...")} に与えて再現する（既存の性質ベースのテストと同じ扱い）。
 *
 * <p>「知らない項目で結果が変わらない」は、確かめの関数が名前の中身（DSL にあるか・どんな文字か）を見ないことで確かめる。本文の JSON
 * の知らない項目は、画面入出力の層の読み取りで捨てられ、この関数に届かない（{@code ReferenceSameResponseIT} で確かめる）。
 */
class ReferenceRowsRequestValidatorPropertyTest {

    @Property
    @Label("the result does not depend on the content of the names, only on their presence")
    void namesDoNotMatter(
            @ForAll String schema,
            @ForAll String table,
            @ForAll String column,
            @ForAll String conditionColumn,
            @ForAll @StringLength(max = 50) String value) {
        List<RawCondition> conditions = List.of(new RawCondition(conditionColumn, value, null, null, null));
        List<RawCondition> fixed = List.of(new RawCondition("name", value, null, null, null));

        Optional<ReferenceRowsQuery> forged =
                ReferenceRowsRequestValidator.validate(schema, table, column, conditions, BigDecimal.ONE, null);
        Optional<ReferenceRowsQuery> plain =
                ReferenceRowsRequestValidator.validate("s", "t", "c", fixed, BigDecimal.ONE, null);

        assertThat(forged.isPresent()).isEqualTo(plain.isPresent());
        assertThat(ReferenceRowsRequestValidator.validate(schema, table, column, null, null, value))
                .isPresent();
    }

    @Property
    @Label("a non-empty value within 1,000 code points with a page in range is never a format error")
    void valuesWithinTheLimitAreAccepted(
            @ForAll @StringLength(min = 1, max = 1_000) String value,
            @ForAll @IntRange(min = 1, max = 1_000_000) int page) {
        assertThat(ReferenceRowsRequestValidator.validate(
                        "s",
                        "t",
                        "c",
                        List.of(new RawCondition("name", value, null, null, null)),
                        BigDecimal.valueOf(page),
                        null))
                .hasValueSatisfying(q -> assertThat(q.offset()).isEqualTo((page - 1) * 20));
        assertThat(ReferenceRowsRequestValidator.validate("s", "t", "c", null, null, value))
                .hasValueSatisfying(q -> assertThat(q.value().value()).isEqualTo(value));
    }
}
