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
package cherry.mastersmith.role.service;

import cherry.mastersmith.dsl.domain.DslFormat;
import cherry.mastersmith.dsl.service.SafeYamlLimits;

/**
 * 権限の YAML の確かめ・適用の上限（BR9.3・NFR1.5・NFR2.9、{@code entities.md} の RoleTransferLimits）。大きさは道ごとの本文の上限
 * （{@code RequestBodyLimitRoute}）と同じ値（計画の D-21。値は設定 {@code mastersmith.role-transfer.max-bytes}、既定 5 MiB）。深さ・
 * コレクションの別名・展開後の節の数・1行の長さの上限は決まった値（1行の長さは DSL と同じ {@link DslFormat#MAX_LINE_LENGTH}。
 * Intent 261015-deps-ui-carryover の FR9.2。超えたら大きさの区分 {@code TOO_LARGE} で拒否する。計画の D-3）。
 *
 * @param maxBytes 本文の大きさの上限（バイト）
 */
public record RoleTransferLimits(int maxBytes) {

    /** 既定の大きさの上限（5 MiB。Build and Test の Q1 で 10 MiB から下げた）。 */
    public static final int DEFAULT_MAX_BYTES = 5_242_880;

    /** 入れ子の深さの上限。 */
    public static final int MAX_DEPTH = 10;

    /** コレクションを指す別名の数の上限。 */
    public static final int MAX_ALIASES = 0;

    /** 別名を展開した後の節の数の上限。 */
    public static final int MAX_EXPANDED_NODES = 500_000;

    /** 1行の長さの上限（コードポイント。DSL と同じ値）。 */
    public static final int MAX_LINE_LENGTH = DslFormat.MAX_LINE_LENGTH;

    /** 大きさが 1 以上であることを確かめる。 */
    public RoleTransferLimits {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes は 1 以上です");
        }
    }

    /**
     * 既定の上限を返す。
     *
     * @return 上限
     */
    public static RoleTransferLimits defaults() {
        return new RoleTransferLimits(DEFAULT_MAX_BYTES);
    }

    /**
     * 上限つきの読み込みに渡す上限にする。
     *
     * @return 読み込みの上限
     */
    public SafeYamlLimits toSafeYamlLimits() {
        return new SafeYamlLimits(maxBytes, MAX_DEPTH, MAX_ALIASES, MAX_EXPANDED_NODES, MAX_LINE_LENGTH);
    }
}
