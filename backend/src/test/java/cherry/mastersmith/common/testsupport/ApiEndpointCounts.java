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
package cherry.mastersmith.common.testsupport;

/**
 * API の口の数の下限（{@code ApiAccessArchitectureTest}・{@code ApiAccessConsistencyIT} が共有する）。
 *
 * <p>口が減ると、印の検査（静的な検査と、起動したアプリの口との比べ）が空振りに近づくため、今ある口の数以上であることを確かめる。
 * 2つのテストに直書きしていた下限（34）をこの1つの定数にまとめ、今の口の数に上げた（Intent 261015-deps-ui-carryover、前の Intent
 * のレビューの cross-cutting R-05）。口を足したら、この値も今の数に上げる（下げるのは口を外したときだけ）。
 */
public final class ApiEndpointCounts {

    /**
     * 今ある API の口の数（2026-10-20 の時点。Intent 261016-business-data-views の U1 で読み取りの3本、U2 で書き込みの3本を足した。静的な
     * 検査の {@code ApiAccessRules.endpointKeys} で数えた）。
     */
    public static final int MIN_ENDPOINTS = 73;

    private ApiEndpointCounts() {}
}
