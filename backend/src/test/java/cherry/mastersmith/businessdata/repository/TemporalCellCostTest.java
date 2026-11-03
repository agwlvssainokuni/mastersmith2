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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 変換の費用の測り（NFR2.1.3・NFR2.1.4）。コンテナ・ネットワーク・モックの {@code ResultSet} を含めず、{@link TemporalCells} だけで、
 * 固定の1万個のセルを整形・読み取りする。暖機の後に5回測り、中央値で合計 500 ミリ秒・平均 0.05 ミリ秒以内を判定する。
 */
class TemporalCellCostTest {

    private static final int CELLS = 10_000;

    private static final long TOTAL_LIMIT_NANOS = 500_000_000L;

    private static final long MEAN_LIMIT_NANOS = 50_000L;

    private static final int RUNS = 5;

    private static int sink;

    /** 時点・時差を持たない日時・日付を除く時刻・時差つきの時刻・桁 0・3・6 を混ぜた1つ分の変換。 */
    private static void normal(int i) {
        int digits = (i % 3) * 3;
        switch (i % 6) {
            case 0 ->
                sink += TemporalCells.formatDateTime(
                                LocalDateTime.of(2026, 1 + i % 12, 1 + i % 28, 3, 4, 5, 123_456_000), digits)
                        .length();
            case 1 ->
                sink += TemporalCells.formatDateTimeTz(
                                OffsetDateTime.of(2026, 1 + i % 12, 1 + i % 28, 3, 4, 5, 123_456_000, ZoneOffset.UTC),
                                digits)
                        .length();
            case 2 ->
                sink += TemporalCells.formatTime(LocalTime.of(i % 24, 4, 5, 123_456_000), digits)
                        .length();
            case 3 ->
                sink += TemporalCells.formatTimeTz(
                                OffsetTime.of(i % 24, 4, 5, 123_456_000, ZoneOffset.ofHours(9)), digits)
                        .length();
            case 4 ->
                sink += TemporalCells.readDateTimeText("2026-03-08 02:30:00.123456", digits)
                        .hashCode();
            default ->
                sink += TemporalCells.readDateTimeText("0000-00-00 00:00:00", digits)
                        .hashCode();
        }
    }

    /** 最悪の場合: すべてが読めない値（日時の解析の失敗で例外を受ける経路を含む）。 */
    private static void worst(int i) {
        switch (i % 3) {
            case 0 -> sink += TemporalCells.readTimeText("838:59:59", 3).hashCode();
            case 1 ->
                sink += TemporalCells.readDateTimeText("2026-13-45 99:99:99", 6).hashCode();
            default ->
                sink += TemporalCells.readDateTimeTzText("garbage" + i, 0).hashCode();
        }
    }

    private static long[] measure(IntConsumer work) {
        // 暖機。
        for (int i = 0; i < CELLS; i++) {
            work.accept(i);
        }
        long[] runs = new long[RUNS];
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < CELLS; i++) {
                work.accept(i);
            }
            runs[run] = System.nanoTime() - start;
        }
        return runs;
    }

    private static void judge(String label, long firstColdNanos, long[] runs) {
        long[] sorted = runs.clone();
        Arrays.sort(sorted);
        long median = sorted[RUNS / 2];
        String report = label + ": cold=" + firstColdNanos + "ns runs=" + Arrays.toString(runs) + " median=" + median
                + "ns mean=" + (median / CELLS) + "ns env=" + System.getProperty("java.version") + "/"
                + System.getProperty("os.arch") + "/cpus="
                + Runtime.getRuntime().availableProcessors();
        System.out.println("[TemporalCellCostTest] " + report);
        assertThat(median).as(report).isLessThanOrEqualTo(TOTAL_LIMIT_NANOS);
        assertThat(median / CELLS).as(report).isLessThanOrEqualTo(MEAN_LIMIT_NANOS);
    }

    @Test
    @DisplayName("10,000 mixed cells are converted within 500 ms in total and 0.05 ms each (median of 5 runs)")
    void mixedCells() {
        long start = System.nanoTime();
        for (int i = 0; i < CELLS; i++) {
            normal(i);
        }
        long cold = System.nanoTime() - start;
        judge("mixed", cold, measure(TemporalCellCostTest::normal));
    }

    @Test
    @DisplayName("10,000 cells that are all unreadable also stay inside the same limits (median of 5 runs)")
    void allUnreadableCells() {
        long start = System.nanoTime();
        for (int i = 0; i < CELLS; i++) {
            worst(i);
        }
        long cold = System.nanoTime() - start;
        judge("worst", cold, measure(TemporalCellCostTest::worst));
    }
}
