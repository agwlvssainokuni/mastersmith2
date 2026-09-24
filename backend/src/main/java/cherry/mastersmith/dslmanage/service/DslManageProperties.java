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

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * DSL の管理の設定（{@code mastersmith.dsl.*}。U4 の Infrastructure Design 3節）。
 *
 * <p>値が正しくないとき（上限が 1 バイト未満、履歴の件数が 1 未満）は、起動を止める（設定の誤りを黙って既定値にしない）。
 *
 * @param maxSubmitSize 投入の API（{@code POST /api/admin/dsl/preview}）だけの要求の本文の上限（既定 10MB = 10,485,760 バイト）
 * @param historyLimit 適用の履歴の件数の上限（既定 20、1 以上。BR4.4）
 */
@ConfigurationProperties("mastersmith.dsl")
public record DslManageProperties(
        @DefaultValue("10MB") DataSize maxSubmitSize,
        @DefaultValue("20") int historyLimit) {

    /** 値の範囲を確かめる。 */
    public DslManageProperties {
        Objects.requireNonNull(maxSubmitSize, "mastersmith.dsl.max-submit-size は必須です");
        if (maxSubmitSize.toBytes() < 1) {
            throw new IllegalArgumentException("mastersmith.dsl.max-submit-size は 1 バイト以上にしてください");
        }
        if (historyLimit < 1) {
            throw new IllegalArgumentException("mastersmith.dsl.history-limit は 1 以上にしてください");
        }
    }
}
