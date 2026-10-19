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
package cherry.mastersmith.businessdata.domain;

/**
 * 画面が要求で送った定義の識別（確かめる前。契約 C2 の {@code definitionId}）。今の識別（{@link DefinitionId}）と比べるだけに使い、形を
 * 確かめない（形の正しくない値も「今と違う」として扱い、判定の順を変えない。BR1.1・BR9.1）。
 *
 * @param dslHash DSL の識別（無ければ null）
 * @param workRoleId 作業ロールの ID（無ければ null）
 */
public record RequestedDefinitionId(String dslHash, Long workRoleId) {

    /** 長さだけを出す（形を確かめていない値をそのまま出さない）。 */
    @Override
    public String toString() {
        return "RequestedDefinitionId[dslHash=" + (dslHash == null ? "null" : dslHash.length() + "chars")
                + ", workRoleId=" + workRoleId + "]";
    }
}
