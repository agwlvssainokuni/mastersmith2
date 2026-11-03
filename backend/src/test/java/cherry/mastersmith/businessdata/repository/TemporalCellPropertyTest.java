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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/** 日時・時刻のセルの整形と読み取りの性質ベースのテスト（jqwik。失敗時は乱数の種と反例が出る）。 */
class TemporalCellPropertyTest {

    @Provide
    Arbitrary<LocalDateTime> dateTimes() {
        return Arbitraries.longs()
                .between(0L, 4_000_000_000L)
                .flatMap(seconds -> Arbitraries.integers()
                        .between(0, 999_999)
                        .map(micros -> LocalDateTime.ofEpochSecond(seconds, micros * 1000, ZoneOffset.UTC)));
    }

    @Property
    @Label("any string never throws out of the reading and an unreadable one is kept as Raw")
    void noExceptionEscapes(
            @ForAll @StringLength(max = 40) String text, @ForAll @IntRange(min = 0, max = 6) int digits) {
        CellValue dateTime = TemporalCells.readDateTimeText(text, digits);
        CellValue time = TemporalCells.readTimeText(text, digits);
        CellValue date = TemporalCells.readDateText(text);
        CellValue tz = TemporalCells.readDateTimeTzText(text, digits);
        for (CellValue cell : new CellValue[] {dateTime, time, date, tz}) {
            assertThat(cell).isInstanceOfAny(CellValue.Text.class, CellValue.Raw.class);
            if (cell instanceof CellValue.Raw raw) {
                assertThat(raw.text()).isEqualTo(text);
            }
        }
    }

    @Property
    @Label("a readable datetime formatted with fixed digits is read back as the same instant")
    void roundTrip(@ForAll("dateTimes") LocalDateTime value) {
        String text = TemporalCells.formatDateTime(value, 6);
        assertThat(LocalDateTime.parse(text)).isEqualTo(value);
        OffsetDateTime zoned = value.atOffset(ZoneOffset.UTC);
        assertThat(OffsetDateTime.parse(TemporalCells.formatDateTimeTz(zoned, 6))
                        .toInstant())
                .isEqualTo(zoned.toInstant());
    }

    @Property(tries = 20)
    @Label("values without an offset do not change when the default time zone changes")
    void defaultZoneDoesNotChangeValues(@ForAll("dateTimes") LocalDateTime value, @ForAll("zones") String zone) {
        TimeZone original = TimeZone.getDefault();
        try {
            String before = TemporalCells.formatDateTime(value, 3);
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            assertThat(TemporalCells.formatDateTime(value, 3)).isEqualTo(before);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Provide
    Arbitrary<String> zones() {
        return Arbitraries.of("Asia/Tokyo", "America/Los_Angeles", "UTC", "Asia/Kathmandu", "Pacific/Auckland");
    }

    @Property
    @Label("the formatted fraction always has exactly the fixed number of digits")
    void digitsAreKept(@ForAll("dateTimes") LocalDateTime value, @ForAll @IntRange(min = 0, max = 6) int digits) {
        String text = TemporalCells.formatDateTime(value, digits);
        int dot = text.indexOf('.');
        if (digits == 0) {
            assertThat(dot).isNegative();
        } else {
            assertThat(text.length() - dot - 1).isEqualTo(digits);
        }
    }
}
