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

import cherry.mastersmith.group.domain.GroupOperation;
import org.springframework.stereotype.Component;

/** 本番の待ち合わせの口（何もしない）。テストは {@code @Primary} の別の部品で差し替える。 */
@Component
public class NoOpGroupBarrier implements GroupBarrier {

    @Override
    public void beforeLock(GroupOperation operation, long groupId) {
        // 本番では待ち合わせない（本番のコードの流れを変えないための既定の部品）。
    }

    @Override
    public void afterLock(GroupOperation operation, long groupId) {
        // 本番では待ち合わせない。
    }

    @Override
    public void afterCheck(GroupOperation operation, String key) {
        // 本番では待ち合わせない。
    }

    @Override
    public void afterWrite(GroupOperation operation, String key) {
        // 本番では待ち合わせない。
    }
}
