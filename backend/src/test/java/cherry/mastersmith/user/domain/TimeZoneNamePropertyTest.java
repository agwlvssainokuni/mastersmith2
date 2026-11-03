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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** 表示のタイムゾーンの名前の門の性質ベースのテスト（SD1 の P1〜P6、jqwik。失敗時の乱数の種はレポートに出る）。 */
class TimeZoneNamePropertyTest {

    private static final List<String> NAMES = new ArrayList<>(TimeZoneName.names());

    @Provide
    Arbitrary<String> members() {
        return Arbitraries.of(NAMES);
    }

    @Property
    void p1EveryMemberPasses(@ForAll("members") String name) {
        assertThat(TimeZoneName.check(name)).isEmpty();
    }

    @Property
    void p2ChangedMembersThatLeaveTheSetFail(
            @ForAll("members") String name, @ForAll("variants") int variant, @ForAll char extra) {
        String changed = switch (variant) {
            case 0 -> name + extra;
            case 1 -> name.substring(1);
            case 2 -> name.substring(0, name.length() - 1);
            default -> name.toLowerCase(Locale.ROOT);
        };
        boolean stillMember = TimeZoneName.names().contains(changed);
        assertThat(TimeZoneName.check(changed).isEmpty()).isEqualTo(stillMember);
    }

    @Provide
    Arbitrary<Integer> variants() {
        return Arbitraries.integers().between(0, 3);
    }

    @Provide
    Arbitrary<String> offsets() {
        Arbitrary<String> sign = Arbitraries.of("+", "-");
        Arbitrary<String> hh = Arbitraries.integers().between(0, 99).map(n -> String.format("%02d", n));
        Arbitrary<String> mm = Arbitraries.integers().between(0, 99).map(n -> String.format("%02d", n));
        return Combinators.combine(sign, hh, mm)
                .as((s, h, m) -> s + h + m)
                .flatMap(raw -> Arbitraries.of(raw, raw.substring(0, 3), raw.substring(0, 3) + ":" + raw.substring(3)));
    }

    @Property
    void p3FixedOffsetsNeverPass(@ForAll("offsets") String offset) {
        assertThat(TimeZoneName.check(offset)).isNotEmpty();
    }

    @Property
    void p4LongValuesNeverPass(@ForAll @net.jqwik.api.constraints.StringLength(min = 65, max = 300) String value) {
        assertThat(TimeZoneName.check(value)).isNotEmpty();
    }

    @Property
    void p5PaddedOrControlMembersFail(
            @ForAll("members") String name, @ForAll("padding") String padding, @ForAll boolean before) {
        String padded = before ? padding + name : name + padding;
        assertThat(TimeZoneName.check(padded)).isNotEmpty();
    }

    @Provide
    Arbitrary<String> padding() {
        return Arbitraries.of(" ", "\t", "\n", "\r", "\u0000", "\u0007", "\u001f", "\u007f", "\u3000", "\u00a0");
    }

    @Property
    void p6TheSameInputGivesTheSameResult(@ForAll String value) {
        assertThat(TimeZoneName.check(value)).isEqualTo(TimeZoneName.check(value));
    }
}
