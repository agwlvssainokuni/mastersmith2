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
package cherry.mastersmith.group.service;

/** 問う口の答え（{@code entities.md} の DeletionDecision、BR4.1・BR6.2）。 */
public sealed interface DeletionDecision permits DeletionDecision.Allowed, DeletionDecision.Blocked {

    /** 割り当てが無く、消してよい。 */
    record Allowed() implements DeletionDecision {}

    /**
     * 割り当てが残り、消せない。
     *
     * @param assignedRoles 割り当ての数（1 以上）
     */
    record Blocked(int assignedRoles) implements DeletionDecision {

        /** 数が 1 以上であることを確かめる。 */
        public Blocked {
            if (assignedRoles < 1) {
                throw new IllegalArgumentException("割り当ての数は 1 以上です: " + assignedRoles);
            }
        }
    }

    /**
     * 割り当ての数を返す（{@link Allowed} なら 0）。
     *
     * @return 割り当ての数
     */
    default int assignedRoles() {
        return this instanceof Blocked blocked ? blocked.assignedRoles() : 0;
    }
}
