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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 日時・時刻のセルの整形と文字の読み取りの単体テスト（LC5、BR3.3・BR3.10）。 */
class TemporalCellsTest {

    @ParameterizedTest(name = "digits {0}")
    @CsvSource({
        "0, 2026-12-31T23:59:59, 2026-12-31T23:59:59+00:00, 23:59:59, 23:59:59+09:00",
        "3, 2026-12-31T23:59:59.120, 2026-12-31T23:59:59.120+00:00, 23:59:59.120, 23:59:59.120+09:00",
        "6, 2026-12-31T23:59:59.120000, 2026-12-31T23:59:59.120000+00:00, 23:59:59.120000, 23:59:59.120000+09:00"
    })
    @DisplayName("the fixed digits keep trailing zeros and digit 0 has no fraction")
    void fixedDigits(int digits, String dateTime, String dateTimeTz, String time, String timeTz) {
        assertThat(TemporalCells.formatDateTime(LocalDateTime.of(2026, 12, 31, 23, 59, 59, 120_000_000), digits))
                .isEqualTo(dateTime);
        assertThat(TemporalCells.formatDateTimeTz(
                        OffsetDateTime.of(2026, 12, 31, 23, 59, 59, 120_000_000, ZoneOffset.UTC), digits))
                .isEqualTo(dateTimeTz);
        assertThat(TemporalCells.formatTime(LocalTime.of(23, 59, 59, 120_000_000), digits))
                .isEqualTo(time);
        assertThat(TemporalCells.formatTimeTz(OffsetTime.of(23, 59, 59, 120_000_000, ZoneOffset.ofHours(9)), digits))
                .isEqualTo(timeTz);
    }

    @Test
    @DisplayName("a null digit count leaves the digits free and an out of range count is clamped, not rounded")
    void freeAndClamped() {
        LocalDateTime value = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123_456_000);
        assertThat(TemporalCells.formatDateTime(value, null)).isEqualTo("2026-01-02T03:04:05.123456");
        assertThat(TemporalCells.formatDateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5), null))
                .isEqualTo("2026-01-02T03:04:05");
        assertThat(TemporalCells.formatDateTime(value, 9)).isEqualTo("2026-01-02T03:04:05.123456");
        assertThat(TemporalCells.formatDateTime(value, -3)).isEqualTo("2026-01-02T03:04:05");
        assertThat(TemporalCells.formatDateTime(value, 3)).as("切り捨て（丸めない）").isEqualTo("2026-01-02T03:04:05.123");
        assertThat(TemporalCells.clampDigits(7)).isEqualTo(6);
        assertThat(TemporalCells.clampDigits(-1)).isZero();
    }

    @Test
    @DisplayName("an offset with a minute fraction is kept as it is")
    void minuteOffsets() {
        assertThat(TemporalCells.formatTimeTz(OffsetTime.of(10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 45)), 0))
                .isEqualTo("10:00:00+05:45");
        assertThat(TemporalCells.formatTimeTz(OffsetTime.of(10, 0, 0, 0, ZoneOffset.ofHoursMinutes(-3, -30)), 0))
                .isEqualTo("10:00:00-03:30");
    }

    @Test
    @DisplayName("zero dates and failed parses keep the text sent by the database, NULL stays NULL")
    void unreadableText() {
        assertThat(TemporalCells.readDateText("0000-00-00")).isEqualTo(new CellValue.Raw("0000-00-00"));
        assertThat(TemporalCells.readDateTimeText("0000-00-00 00:00:00", 0))
                .isEqualTo(new CellValue.Raw("0000-00-00 00:00:00"));
        assertThat(TemporalCells.readDateTimeTzText("0000-00-00 00:00:00", 0))
                .isEqualTo(new CellValue.Raw("0000-00-00 00:00:00"));
        assertThat(TemporalCells.readTimeText("838:59:59", 0)).isEqualTo(new CellValue.Raw("838:59:59"));
        assertThat(TemporalCells.readTimeText("-01:00:00", 0)).isEqualTo(new CellValue.Raw("-01:00:00"));
        assertThat(TemporalCells.readDateTimeText("not a date", null)).isEqualTo(new CellValue.Raw("not a date"));
        assertThat(TemporalCells.readDateText(null)).isEqualTo(new CellValue.Text(null, false));
        assertThat(TemporalCells.readTimeText(null, 3)).isEqualTo(new CellValue.Text(null, false));
        assertThat(TemporalCells.raw(null)).isEqualTo(new CellValue.Text(null, false));
    }

    @Test
    @DisplayName("readable text is formatted by the column digits, not by the length of the driver text")
    void readableText() {
        assertThat(TemporalCells.readDateTimeText("2026-03-08 02:30:00.123000", 3))
                .isEqualTo(new CellValue.Text("2026-03-08T02:30:00.123", false));
        assertThat(TemporalCells.readDateTimeText("2026-03-08 02:30:00", 6))
                .isEqualTo(new CellValue.Text("2026-03-08T02:30:00.000000", false));
        assertThat(TemporalCells.readDateTimeTzText("2026-03-08 02:30:00.5", 1))
                .isEqualTo(new CellValue.Text("2026-03-08T02:30:00.5+00:00", false));
        assertThat(TemporalCells.readTimeText("10:30:00.25", null)).isEqualTo(new CellValue.Text("10:30:00.25", false));
        assertThat(TemporalCells.readDateText("2024-02-29")).isEqualTo(new CellValue.Text("2024-02-29", false));
        assertThat(TemporalCells.formatDate(LocalDate.of(2026, 12, 31))).isEqualTo("2026-12-31");
    }

    @Test
    @DisplayName("the formatting does not depend on the default time zone of the JVM")
    void defaultZoneIsNotUsed() {
        TimeZone original = TimeZone.getDefault();
        try {
            OffsetDateTime value = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            String before = TemporalCells.formatDateTimeTz(value, 3);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            assertThat(TemporalCells.formatDateTimeTz(value, 3)).isEqualTo(before);
            assertThat(TemporalCells.readDateTimeTzText("2026-01-01 00:00:00.000", 3))
                    .isEqualTo(new CellValue.Text("2026-01-01T00:00:00.000+00:00", false));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
