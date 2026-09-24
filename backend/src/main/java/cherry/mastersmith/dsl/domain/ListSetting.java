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
package cherry.mastersmith.dsl.domain;

/**
 * 一覧表示（値）。
 *
 * @param visible 一覧に出すなら true
 * @param order 並び順（1 から。{@code visible} が true のとき必須。無ければ null）
 * @param width 幅（1 以上。無ければ null）
 * @param sortable 並べ替えられるなら true
 * @param defaultSort 既定の並びの向き（無ければ null）
 * @param format 表示の書式（無ければ null）
 */
public record ListSetting(
        boolean visible, Integer order, Integer width, boolean sortable, SortDirection defaultSort, ListFormat format) {

    /** 一覧に出すときは並び順があり、並び順と幅が 1 以上であることを確かめる。 */
    public ListSetting {
        if (visible && order == null) {
            throw new IllegalArgumentException("list.order は visible が true のとき必須です");
        }
        if (order != null && order < 1) {
            throw new IllegalArgumentException("list.order は 1 以上です");
        }
        if (width != null && width < 1) {
            throw new IllegalArgumentException("list.width は 1 以上です");
        }
    }
}
