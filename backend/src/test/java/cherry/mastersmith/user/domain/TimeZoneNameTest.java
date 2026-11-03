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
package cherry.mastersmith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 表示のタイムゾーンの名前の門の単体テスト（BR2.2・BR2.4、SD1・PD5）。 */
class TimeZoneNameTest {

    private static final Optional<FieldErrorReason> INVALID = Optional.of(FieldErrorReason.INVALID_VALUE);

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Tokyo", "America/New_York", "Asia/Kolkata", "Asia/Kathmandu", "UTC", "Europe/London"})
    @DisplayName("names in the JDK region set pass")
    void setMembersPass(String name) {
        assertThat(TimeZoneName.check(name)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " Asia/Tokyo",
                "Asia/Tokyo ",
                "Asia/Tokyo\n",
                "Asia/Tokyo\u0000",
                "Asia\u0007/Tokyo",
                "asia/tokyo",
                "ASIA/TOKYO",
                "+09:00",
                "-03:30",
                "+0900",
                "+09",
                "Z",
                "UTC+9",
                "GMT+9",
                "Asia/Tokio",
                "Mars/Olympus"
            })
    @DisplayName("empty, padded, control characters, other case, fixed offsets and unknown names are rejected")
    void invalidNamesAreRejected(String name) {
        assertThat(TimeZoneName.check(name)).isEqualTo(INVALID);
    }

    @Test
    @DisplayName("null is rejected without consulting the set")
    void nullIsRejected() {
        assertThat(TimeZoneName.check(null)).isEqualTo(INVALID);
    }

    @Test
    @DisplayName("a value of 65 characters is rejected, a value of 64 characters is judged by the set")
    void lengthBoundary() {
        assertThat(TimeZoneName.check("a".repeat(65))).isEqualTo(INVALID);
        assertThat(TimeZoneName.check("a".repeat(64))).isEqualTo(INVALID);
        assertThat(ZoneId.getAvailableZoneIds())
                .allSatisfy(id -> assertThat(id.length()).isLessThanOrEqualTo(64));
    }

    @Test
    @DisplayName("every JDK region name passes")
    void everyJdkNamePasses() {
        assertThat(ZoneId.getAvailableZoneIds())
                .allSatisfy(id -> assertThat(TimeZoneName.check(id)).isEmpty());
    }

    @Test
    @DisplayName("the set is built once and cannot be modified")
    void setIsPrebuiltAndImmutable() {
        assertThat(TimeZoneName.names()).isSameAs(TimeZoneName.names()).contains("UTC", "Asia/Tokyo");
        assertThatThrownBy(() -> TimeZoneName.names().add("Mars/Olympus"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> TimeZoneName.names().remove("UTC")).isInstanceOf(UnsupportedOperationException.class);
    }
}
