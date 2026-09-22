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
package cherry.mastersmith.common.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 差し込み口の起動時の検査（security-design 3章）。
 *
 * <p>{@link SecurityRuleContributor} の order の重複と、{@link ApiDefaultAccess} が2つ以上あることを検出して起動を失敗させ、
 * 追加の決まりを呼ぶ順（order の小さい順）に並べる。
 */
public final class SecurityExtensionValidator {

    private SecurityExtensionValidator() {}

    /**
     * 追加の決まりの order の重複を確かめ、呼ぶ順に並べる。
     *
     * @param contributors 追加の決まり
     * @return order の小さい順に並べた一覧
     * @throws IllegalStateException order が重複しているとき
     */
    public static List<SecurityRuleContributor> sortedContributors(List<SecurityRuleContributor> contributors) {
        Map<Integer, Long> counts = contributors.stream()
                .collect(Collectors.groupingBy(SecurityRuleContributor::getOrder, Collectors.counting()));
        TreeSet<Integer> duplicates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("SecurityRuleContributor の order が重複しています: " + duplicates);
        }
        List<SecurityRuleContributor> sorted = new ArrayList<>(contributors);
        sorted.sort(Comparator.comparingInt(SecurityRuleContributor::getOrder));
        return List.copyOf(sorted);
    }

    /**
     * API の既定の扱いが0個か1個であることを確かめる。
     *
     * @param accesses API の既定の扱い
     * @return 1つあればそれ、無ければ空
     * @throws IllegalStateException 2つ以上あるとき
     */
    public static Optional<ApiDefaultAccess> singleDefaultAccess(List<ApiDefaultAccess> accesses) {
        if (accesses.size() > 1) {
            throw new IllegalStateException("ApiDefaultAccess は1つまでです（" + accesses.size() + " 個あります）");
        }
        return accesses.stream().findFirst();
    }
}
