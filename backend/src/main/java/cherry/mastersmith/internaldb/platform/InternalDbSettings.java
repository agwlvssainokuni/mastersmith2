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
package cherry.mastersmith.internaldb.platform;

import java.util.Optional;
import org.springframework.core.env.Environment;

/**
 * 内部DB の種類の判断の結果を持つ値（起動時に 1 回だけ作る）。{@code spring.datasource.url} が環境に無いときは、判断をしない（空）。
 * URL の値そのものは持たない。
 *
 * @param resolution 判断の結果（{@code spring.datasource.url} が環境に無いときは空）
 */
public record InternalDbSettings(Optional<InternalDbUrlResolution> resolution) {

    /** 内部DB の接続先を読む設定の名前。 */
    public static final String URL_PROPERTY = "spring.datasource.url";

    /**
     * 環境から {@code spring.datasource.url} を 1 回読み、種類を判断する。
     *
     * @param environment Spring の環境
     * @return 判断の結果を持つ値
     */
    public static InternalDbSettings from(Environment environment) {
        String url = environment.getProperty(URL_PROPERTY);
        if (url == null && !environment.containsProperty(URL_PROPERTY)) {
            return new InternalDbSettings(Optional.empty());
        }
        return new InternalDbSettings(Optional.of(InternalDbUrlResolver.resolve(url)));
    }

    /**
     * 対応する種類と判断できたときの種類。
     *
     * @return 種類（判断をしていない・対応しない・空のときは空）
     */
    public Optional<InternalDbKind> kind() {
        return resolution
                .filter(InternalDbUrlResolution.Supported.class::isInstance)
                .map(r -> ((InternalDbUrlResolution.Supported) r).kind());
    }
}
