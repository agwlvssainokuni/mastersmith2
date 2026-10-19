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

import static cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture.column;
import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastersmith.businessdata.repository.ColumnValueCodec;
import cherry.mastersmith.businessdata.testsupport.BusinessDataDslFixture;
import cherry.mastersmith.dsl.domain.DslColumn;
import cherry.mastersmith.dsl.domain.DslTable;
import cherry.mastersmith.role.domain.MainPermission;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 検証の全体の実時間の単体テスト（Intent 261016-business-data-views の U2、NFR1.6、performance-design.md の 3節）。締め切りの判定は
 * 注入した時計で確かめ、ここでは実時間の線（400 ms）だけを測る。
 */
class DeadlinePatternTimingTest {

    @Test
    @DisplayName("validating 100 columns with heavy patterns and long values finishes within 400 ms of real time")
    void hundredHeavyColumns() {
        List<DslColumn> columns = new ArrayList<>();
        columns.add(column("id").dbType("int4").build());
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            String name = "c" + i;
            columns.add(column(name)
                    .dbType("text")
                    .pattern(i % 2 == 0 ? "((a+)+)+c" : "(.*a){12}", null)
                    .build());
            values.put(name, "a".repeat(30) + "!" + "a".repeat(9_000));
        }
        DslTable table = BusinessDataDslFixture.table("wide", false, List.of("id"), columns.toArray(DslColumn[]::new));
        TableAccessView view = BusinessDataDslFixture.view(table, MainPermission.FULL, Map.of());
        DeadlinePatternMatcher matcher = new DeadlinePatternMatcher(Clock.systemUTC());
        // 部品を読み込む分を測りに入れないよう、1回先に流す。
        WriteRequestValidator.validate(
                view,
                WriteOperation.CREATE,
                new KeyInput(null),
                new WriteValues(values),
                new ColumnValueCodec(),
                matcher);

        long start = System.nanoTime();
        WriteValidation validation = WriteRequestValidator.validate(
                view,
                WriteOperation.CREATE,
                new KeyInput(null),
                new WriteValues(values),
                new ColumnValueCodec(),
                matcher);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(validation).isInstanceOf(WriteValidation.Invalid.class);
        assertThat(((WriteValidation.Invalid) validation).errors())
                .allSatisfy(e -> assertThat(e.reason()).isEqualTo(BusinessDataFieldErrorReason.PATTERN_TIMEOUT));
        assertThat(elapsedMillis).as("実時間の線（300 ms の合計 + 超えと揺れの 100 ms）").isLessThanOrEqualTo(400);
    }
}
