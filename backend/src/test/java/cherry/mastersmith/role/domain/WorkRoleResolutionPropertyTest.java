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
package cherry.mastersmith.role.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * 有効な作業ロールの決め方の性質ベースのテスト（jqwik。BR7.1・BR7.2・BR7.4・BR7.6、AC4.1.16・AC4.1.21、NFR6.2）。
 *
 * <p>名前は {@code Properties} で終わらせない（{@code *Test}・{@code *IT} のどちらにも当たらず動かないため。計画の D-12）。失敗のときの
 * 乱数の種は、既存のテストの出力の設定（{@code exceptionFormat = FULL}）で残る。
 */
class WorkRoleResolutionPropertyTest {

    @Provide
    Arbitrary<Set<Long>> roleIds() {
        return Arbitraries.longs().between(1, 40).set().ofMaxSize(12);
    }

    @Provide
    Arbitrary<OptionalLong> stored() {
        return Arbitraries.longs()
                .between(1, 40)
                .injectNull(0.3)
                .map(id -> id == null ? OptionalLong.empty() : OptionalLong.of(id));
    }

    @Property
    @Label("the result is one of the user's roles, and there is no result only when the user has no role")
    void resultIsInTheSet(@ForAll("roleIds") Set<Long> roles, @ForAll("stored") OptionalLong stored) {
        OptionalLong chosen = WorkRoleChooser.choose(roles, stored);

        assertThat(chosen.isPresent()).isEqualTo(!roles.isEmpty());
        chosen.ifPresent(id -> assertThat(roles).contains(id));
    }

    @Property
    @Label("the stored role is the result when it is in the set, and otherwise the smallest id is")
    void storedOrSmallest(@ForAll("roleIds") Set<Long> roles, @ForAll("stored") OptionalLong stored) {
        OptionalLong chosen = WorkRoleChooser.choose(roles, stored);

        if (stored.isPresent() && roles.contains(stored.getAsLong())) {
            assertThat(chosen).isEqualTo(stored);
        } else if (!roles.isEmpty()) {
            assertThat(chosen)
                    .hasValue(roles.stream().mapToLong(Long::longValue).min().orElseThrow());
        }
    }

    @Property
    @Label("after an explicit choice, adding or removing other roles does not change the work role (AC4.1.21)")
    void explicitChoiceIsStable(
            @ForAll("roleIds") Set<Long> roles,
            @ForAll("roleIds") Set<Long> added,
            @ForAll("roleIds") Set<Long> removed) {
        if (roles.isEmpty()) {
            return;
        }
        long selected = roles.iterator().next();
        OptionalLong storedAfterSwitch = OptionalLong.of(selected);
        Set<Long> changed = new HashSet<>(roles);
        changed.addAll(added);
        changed.removeAll(removed);
        changed.add(selected);

        assertThat(WorkRoleChooser.choose(roles, storedAfterSwitch)).hasValue(selected);
        assertThat(WorkRoleChooser.choose(changed, storedAfterSwitch)).hasValue(selected);
    }

    @Property
    @Label("reading does not depend on the order of the role ids")
    void orderDoesNotMatter(@ForAll("roleIds") Set<Long> roles, @ForAll("stored") OptionalLong stored) {
        List<Long> ascending = roles.stream().sorted().toList();
        List<Long> descending = ascending.reversed();

        assertThat(WorkRoleChooser.choose(ascending, stored)).isEqualTo(WorkRoleChooser.choose(descending, stored));
    }
}
