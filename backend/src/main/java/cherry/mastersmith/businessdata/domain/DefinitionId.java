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

import java.util.HexFormat;
import java.util.Objects;

/**
 * テーブルの定義の識別（契約 C2）。適用中の DSL の識別と作業ロールの組で、画面が古い定義を使っているかを見分ける（BR9.1）。
 *
 * @param dslHash 適用中の DSL の識別（16 進数の小文字 64 文字）
 * @param workRoleId 作業ロールの ID（無ければ null）
 */
public record DefinitionId(String dslHash, Long workRoleId) {

    /** 識別の形を確かめる。 */
    public DefinitionId {
        if (dslHash == null
                || dslHash.length() != 64
                || !dslHash.chars().allMatch(c -> HexFormat.isHexDigit(c) && !Character.isUpperCase(c))) {
            throw new IllegalArgumentException("dslHash は 16 進数の小文字 64 文字です");
        }
    }

    /**
     * 要求の識別が今の識別と違うかを返す（BR9.1・BR9.2）。形の正しくない識別も、今と違うものとして扱う。
     *
     * @param requested 要求の識別（無ければ null）
     * @return 要求の識別があり、今と違えば true
     */
    public boolean changedFrom(RequestedDefinitionId requested) {
        return requested != null
                && !(dslHash.equals(requested.dslHash()) && Objects.equals(workRoleId, requested.workRoleId()));
    }
}
