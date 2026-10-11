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
package cherry.mastersmith.role.web;

import cherry.mastersmith.role.service.RoleTransferLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 権限の YAML の確かめ・適用の本文の大きさの上限の設定値（{@code mastersmith.role-transfer.max-bytes}、既定 10,485,760。計画の D-21、
 * NFR2.9）。本番の {@code application.yaml} には書かず既定の値のまま使い、テストで上限を小さくするためだけに置く設定値だけの record。
 *
 * @param maxBytes 本文の大きさの上限（バイト。1 以上）
 */
@ConfigurationProperties("mastersmith.role-transfer")
public record RoleTransferLimitProperties(
        @DefaultValue("10485760") int maxBytes) {

    /** 上限が 1 以上であることを確かめる。 */
    public RoleTransferLimitProperties {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("mastersmith.role-transfer.max-bytes は 1 以上にしてください");
        }
    }

    /**
     * 業務処理に渡す上限にする。
     *
     * @return 上限
     */
    public RoleTransferLimits toLimits() {
        return new RoleTransferLimits(maxBytes);
    }
}
