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

import cherry.mastersmith.role.domain.PermissionNode;
import java.util.List;

/** 権限の設定の木の1階層の読み取りの結果（FS の 2.5、BR4.5・BR4.10〜BR4.12）。読み取りのため、どの結果も監査に残さない。 */
public sealed interface PermissionTreeResult
        permits PermissionTreeResult.Found, PermissionTreeResult.RoleNotFound, PermissionTreeResult.DslNotApplied {

    /**
     * 節の一覧（DSL の順、今の DSL に無い節はその後）。
     *
     * @param nodes 節
     */
    record Found(List<PermissionNode> nodes) implements PermissionTreeResult {

        /** 節を写して持つ。 */
        public Found {
            nodes = List.copyOf(nodes);
        }

        /** 件数だけを出す（D-5）。 */
        @Override
        public String toString() {
            return "Found[nodes=" + nodes.size() + "]";
        }
    }

    /** ロールがいない（404 {@code ROLE_NOT_FOUND}）。 */
    record RoleNotFound() implements PermissionTreeResult {}

    /** 適用済みの DSL が無い（409 {@code DSL_NOT_APPLIED}）。 */
    record DslNotApplied() implements PermissionTreeResult {}
}
