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
package cherry.mastersmith.businessdata.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 止めた段の確かめ（R-05）の単体テスト。ドライバーの分け方が変わった疑似の相手で、原因の分かる失敗になることを確かめる。 */
class StallStageTest {

    private static final String REPORT = "stalled=[chunk#2(87B)]";

    @Test
    @DisplayName("the stage as expected passes, with or without statements before the stall")
    void expectedStagePasses() {
        assertThatCode(() -> StallStage.require(
                        List.of("SET sql_select_limit=1"), List.of("SET sql_select_limit=1"), REPORT))
                .doesNotThrowAnyException();
        assertThatCode(() -> StallStage.require(List.of(), List.of(), REPORT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a driver that sends the statements in other chunks fails with the statements reached and the report")
    void changedChunkingFailsWithTheCause() {
        // 疑似のドライバー: 件数の前の SET を件数と同じまとまりで送る（届いた文が無い）／SET を先に別のまとまりで2つ送る。
        assertThatThrownBy(() -> StallStage.require(List.of("SET sql_select_limit=1"), List.of(), REPORT))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("止まった段が想定と違います")
                .hasMessageContaining("[SET sql_select_limit=1]")
                .hasMessageContaining("実際は []")
                .hasMessageContaining(REPORT)
                .hasMessageContaining("ドライバーの版");
        assertThatThrownBy(() -> StallStage.require(
                        List.of(), List.of("SET sql_select_limit=1", "SET sql_select_limit=20"), REPORT))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SET sql_select_limit=20");
    }

    @Test
    @DisplayName("the order of the statements matters")
    void orderMatters() {
        assertThat(List.of("a", "b")).isNotEqualTo(List.of("b", "a"));
        assertThatThrownBy(() -> StallStage.require(List.of("a", "b"), List.of("b", "a"), REPORT))
                .isInstanceOf(AssertionError.class);
    }
}
