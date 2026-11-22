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

/**
 * 内部DB の接続先の URL の先頭から、種類を判断する純粋な関数（BR1.1・BR1.3・BR1.4）。例外を投げず、値を記録しない。
 *
 * <p>前方一致で、大文字と小文字を区別し、前後の空白は取らない。起動の入口の部品（{@link InternalDbPoolCustomizer}）と
 * {@link InternalDbSettings} が同じ関数を呼ぶため、判断は 1 か所に決まる。
 */
public final class InternalDbUrlResolver {

    private InternalDbUrlResolver() {}

    /**
     * 接続先の URL から種類を判断する。
     *
     * @param url 接続先の URL（{@code null} 可）
     * @return 判断の結果
     */
    public static InternalDbUrlResolution resolve(String url) {
        if (url == null || url.isBlank()) {
            return new InternalDbUrlResolution.Empty();
        }
        for (InternalDbKind kind : InternalDbKind.values()) {
            if (url.startsWith(kind.urlPrefix())) {
                return new InternalDbUrlResolution.Supported(kind);
            }
        }
        return new InternalDbUrlResolution.Unsupported();
    }
}
