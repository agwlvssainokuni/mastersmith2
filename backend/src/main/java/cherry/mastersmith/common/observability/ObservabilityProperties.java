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
package cherry.mastersmith.common.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 観測性の設定（{@code mastersmith.observability.*}）。
 *
 * @param export 外部エクスポートの設定
 */
@ConfigurationProperties("mastersmith.observability")
public record ObservabilityProperties(@DefaultValue Export export) {

    /**
     * 外部エクスポート（トレース・ログ・指標の OTLP の送信）の設定。
     *
     * <p>{@code enabled} の1つで3つの信号の送信をまとめて切り替える（application.yaml で Spring Boot の設定に対応づける）。
     *
     * @param enabled 有効にするか（既定は無効）
     * @param endpoint OTLP の受け手（HTTP）のベースURL
     */
    public record Export(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("http://localhost:4318") String endpoint) {}
}
