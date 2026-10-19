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
package cherry.mastersmith.businessdata.web;

import cherry.mastersmith.businessdata.domain.DefinitionId;
import cherry.mastersmith.businessdata.domain.RequestedDefinitionId;

/**
 * 定義の識別（契約 C2 の {@code DefinitionId}）。要求と応答の両方に使う。
 *
 * @param dslHash 適用中の DSL の識別
 * @param workRoleId 作業ロールの ID（無ければ null）
 */
public record DefinitionIdBody(String dslHash, Long workRoleId) {

    /**
     * 今の識別から作る。
     *
     * @param id 今の識別
     * @return 応答の識別
     */
    static DefinitionIdBody from(DefinitionId id) {
        return new DefinitionIdBody(id.dslHash(), id.workRoleId());
    }

    /**
     * 要求の識別にする（形は確かめない）。
     *
     * @param body 要求の識別（無ければ null）
     * @return 要求の識別（無ければ null）
     */
    static RequestedDefinitionId toRequested(DefinitionIdBody body) {
        return body == null ? null : new RequestedDefinitionId(body.dslHash(), body.workRoleId());
    }

    /** 長さだけを出す。 */
    @Override
    public String toString() {
        return "DefinitionIdBody[dslHash=" + (dslHash == null ? "null" : dslHash.length() + "chars") + ", workRoleId="
                + workRoleId + "]";
    }
}
