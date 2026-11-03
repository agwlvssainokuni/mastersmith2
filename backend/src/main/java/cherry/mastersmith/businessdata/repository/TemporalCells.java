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

import cherry.mastersmith.businessdata.domain.ValueType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * 日時・時刻のセルの整形と、文字で受けた値の読み取りの純粋な部分（LC5、BR3.3・BR3.10）。JDBC の文を作らず・実行しない。書式は桁ごとに
 * 1回だけ作って使い回し、{@link ZoneOffset#UTC} を明示に使う（JVM の既定のタイムゾーンを暗に使わない）。桁を渡すと端数をその桁ちょうどで
 * 出し（末尾の 0 を落とさない・桁 0 は端数なし・丸めない）、桁が null なら桁を固定せずに出す。
 */
final class TemporalCells {

    /** 桁の最大（MySQL・MariaDB・PostgreSQL の日時・時刻の端数は 6 桁まで）。 */
    static final int MAX_DIGITS = 6;

    /** 桁を固定しない書式の端数（0〜9 桁、末尾の 0 は出さない）。 */
    private static final DateTimeFormatter FREE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter[] DATE_TIME = new DateTimeFormatter[MAX_DIGITS + 1];

    private static final DateTimeFormatter[] DATE_TIME_TZ = new DateTimeFormatter[MAX_DIGITS + 1];

    private static final DateTimeFormatter[] TIME = new DateTimeFormatter[MAX_DIGITS + 1];

    private static final DateTimeFormatter[] TIME_TZ = new DateTimeFormatter[MAX_DIGITS + 1];

    private static final DateTimeFormatter FREE_DATE_TIME = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(FREE_TIME)
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter FREE_DATE_TIME_TZ = new DateTimeFormatterBuilder()
            .append(FREE_DATE_TIME)
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter FREE_TIME_TZ = new DateTimeFormatterBuilder()
            .append(FREE_TIME)
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter(Locale.ROOT);

    static {
        for (int digits = 0; digits <= MAX_DIGITS; digits++) {
            DateTimeFormatterBuilder timeBuilder = new DateTimeFormatterBuilder().appendPattern("HH:mm:ss");
            if (digits > 0) {
                // 桁ちょうど（末尾の 0 を落とさない）。桁 0 は端数なし。
                timeBuilder.appendFraction(ChronoField.NANO_OF_SECOND, digits, digits, true);
            }
            DateTimeFormatter time = timeBuilder.toFormatter(Locale.ROOT);
            TIME[digits] = time;
            DATE_TIME[digits] = new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE)
                    .appendLiteral('T')
                    .append(time)
                    .toFormatter(Locale.ROOT);
            DATE_TIME_TZ[digits] = new DateTimeFormatterBuilder()
                    .append(DATE_TIME[digits])
                    .appendOffset("+HH:MM", "+00:00")
                    .toFormatter(Locale.ROOT);
            TIME_TZ[digits] = new DateTimeFormatterBuilder()
                    .append(time)
                    .appendOffset("+HH:MM", "+00:00")
                    .toFormatter(Locale.ROOT);
        }
    }

    private TemporalCells() {}

    /**
     * 日時・時刻の型かを返す。
     *
     * @param type 受け渡しの型
     * @return 日付・日時・時刻のどれかなら true
     */
    static boolean isTemporal(ValueType type) {
        return switch (type) {
            case DATE, DATETIME, DATETIME_TZ, TIME, TIME_TZ -> true;
            default -> false;
        };
    }

    /**
     * 桁を 0〜6 に収める（範囲外は丸めずに端へ寄せる）。
     *
     * @param digits 桁
     * @return 収めた桁
     */
    static int clampDigits(int digits) {
        return Math.min(Math.max(digits, 0), MAX_DIGITS);
    }

    static String formatDate(LocalDate value) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(value);
    }

    static String formatDateTime(LocalDateTime value, Integer digits) {
        return (digits == null ? FREE_DATE_TIME : DATE_TIME[clampDigits(digits)]).format(value);
    }

    /** UTC にそろえた時点を文字にする。 */
    static String formatDateTimeTz(OffsetDateTime value, Integer digits) {
        return (digits == null ? FREE_DATE_TIME_TZ : DATE_TIME_TZ[clampDigits(digits)]).format(value);
    }

    static String formatTime(LocalTime value, Integer digits) {
        return (digits == null ? FREE_TIME : TIME[clampDigits(digits)]).format(value);
    }

    /** 時差のまま文字にする。 */
    static String formatTimeTz(OffsetTime value, Integer digits) {
        return (digits == null ? FREE_TIME_TZ : TIME_TZ[clampDigits(digits)]).format(value);
    }

    /** 読めない値のセル（DB が送った文字のまま）。文字が無ければ NULL のセル。 */
    static CellValue raw(String text) {
        return text == null ? new CellValue.Text(null, false) : new CellValue.Raw(text);
    }

    /** MySQL・MariaDB のゼロの日付・日時（{@code 0000-}）を、例外なしで見分ける。 */
    private static boolean isZeroDate(String text) {
        return text.startsWith("0000-");
    }

    /** 文字で受けた日付を読む。読めなければ DB の文字のまま。 */
    static CellValue readDateText(String text) {
        if (text == null) {
            return new CellValue.Text(null, false);
        }
        if (isZeroDate(text)) {
            return new CellValue.Raw(text);
        }
        try {
            return new CellValue.Text(formatDate(LocalDate.parse(text)), false);
        } catch (RuntimeException e) {
            return new CellValue.Raw(text);
        }
    }

    /** 文字で受けた日時を読む（区切りの空白・ISO の {@code T} の両方）。読めなければ DB の文字のまま。 */
    static CellValue readDateTimeText(String text, Integer digits) {
        if (text == null) {
            return new CellValue.Text(null, false);
        }
        if (isZeroDate(text)) {
            return new CellValue.Raw(text);
        }
        try {
            return new CellValue.Text(formatDateTime(parseLocalDateTime(text), digits), false);
        } catch (RuntimeException e) {
            return new CellValue.Raw(text);
        }
    }

    /** 文字で受けた UTC の日時（MySQL・MariaDB の {@code timestamp}）を、時点として読む。 */
    static CellValue readDateTimeTzText(String text, Integer digits) {
        if (text == null) {
            return new CellValue.Text(null, false);
        }
        if (isZeroDate(text)) {
            return new CellValue.Raw(text);
        }
        try {
            return new CellValue.Text(
                    formatDateTimeTz(parseLocalDateTime(text).atOffset(ZoneOffset.UTC), digits), false);
        } catch (RuntimeException e) {
            return new CellValue.Raw(text);
        }
    }

    /** 文字で受けた時刻を読む。24 時間を超える値・負の値は読めない値として DB の文字のまま。 */
    static CellValue readTimeText(String text, Integer digits) {
        if (text == null) {
            return new CellValue.Text(null, false);
        }
        try {
            return new CellValue.Text(formatTime(LocalTime.parse(text), digits), false);
        } catch (RuntimeException e) {
            return new CellValue.Raw(text);
        }
    }

    /**
     * MySQL・MariaDB が文字で送る日時（{@code 2026-03-08 02:30:00.123456}）を読む。
     *
     * @param text 文字
     * @return 日時
     */
    static LocalDateTime parseLocalDateTime(String text) {
        return LocalDateTime.parse(text.replace(' ', 'T'));
    }
}
