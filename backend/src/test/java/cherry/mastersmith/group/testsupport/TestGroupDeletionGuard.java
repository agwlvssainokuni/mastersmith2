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
package cherry.mastersmith.group.testsupport;

import cherry.mastersmith.group.service.DeletionDecision;
import cherry.mastersmith.group.service.GroupDeletionGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 問う口のテスト用の実装（BR6.3。B3 では割り当ての表が無いため、割り当てが残るときの拒否と数の応答をこれで確かめる）。グループごとの
 * 割り当ての数を決められ、決めていないグループは 0 を返す。
 */
public class TestGroupDeletionGuard implements GroupDeletionGuard {

    private final Map<Long, Integer> counts = new ConcurrentHashMap<>();

    /**
     * グループの割り当ての数を決める。
     *
     * @param groupId グループの ID
     * @param assignedRoles 割り当ての数
     */
    public void assign(long groupId, int assignedRoles) {
        counts.put(groupId, assignedRoles);
    }

    /** 決めた数を消す。 */
    public void reset() {
        counts.clear();
    }

    @Override
    public DeletionDecision canDelete(long groupId) {
        int assigned = counts.getOrDefault(groupId, 0);
        return assigned == 0 ? new DeletionDecision.Allowed() : new DeletionDecision.Blocked(assigned);
    }

    @Override
    public Map<Long, Integer> assignedRoleCounts(Set<Long> groupIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            result.put(groupId, counts.getOrDefault(groupId, 0));
        }
        return result;
    }

    /** テストで差し替える設定。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        /**
         * B3 の仮の実装の代わりに使う。
         *
         * @return 問う口
         */
        @Bean
        @Primary
        public TestGroupDeletionGuard testGroupDeletionGuard() {
            return new TestGroupDeletionGuard();
        }
    }
}
