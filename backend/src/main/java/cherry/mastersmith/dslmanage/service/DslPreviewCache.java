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
package cherry.mastersmith.dslmanage.service;

import cherry.mastersmith.dsl.domain.DslModel;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * プレビューの読み込みの結果（U2 のモデル）を、同じ previewId の間だけアプリの中に1つ持つ（NFR 設計の performance-design.md 1節）。
 *
 * <p>プレビューを置き換え・破棄・適用したら捨てる。持つのは1つだけなので、10MB の DSL のモデルも同時に1つ分だけになる。
 */
@Component
public class DslPreviewCache {

    private final AtomicReference<Entry> entry = new AtomicReference<>();

    /**
     * previewId のモデルを返す。
     *
     * @param previewId プレビューの識別
     * @return モデル（持っているのが別の previewId、または何も持っていなければ空）
     */
    public Optional<DslModel> get(UUID previewId) {
        Entry current = entry.get();
        return current != null && current.previewId().equals(previewId)
                ? Optional.of(current.model())
                : Optional.empty();
    }

    /**
     * previewId のモデルを持つ（前に持っていたものは捨てる）。
     *
     * @param previewId プレビューの識別
     * @param model モデル
     */
    public void put(UUID previewId, DslModel model) {
        entry.set(new Entry(previewId, model));
    }

    /** 持っているものを捨てる。 */
    public void clear() {
        entry.set(null);
    }

    private record Entry(UUID previewId, DslModel model) {}
}
