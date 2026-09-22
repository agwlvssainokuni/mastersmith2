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
package cherry.mastersmith.common.error.web;

import cherry.mastersmith.common.web.MastersmithWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * エラー応答の type の URL の元になるベースURLを決める（BR5.10、NFR3.8）。
 *
 * <p>設定のベースURL（{@code mastersmith.web.base-url}）があればそれを使い、無ければ要求のスキーム・Host・ポートから組み立てる。
 * 転送元のヘッダーは、信頼する設定を有効にしたときだけ、Spring の {@code ForwardedHeaderFilter} が要求に反映する（既定では
 * 登録しないため、ここで見る要求は転送元のヘッダーを含まない）。
 */
@Component
public class ProblemBaseUrlResolver {

    private final String configuredBaseUrl;

    /**
     * ベースURLの決め方を作る。
     *
     * @param properties Web の設定
     */
    public ProblemBaseUrlResolver(MastersmithWebProperties properties) {
        String baseUrl = properties.baseUrl();
        this.configuredBaseUrl = baseUrl == null || baseUrl.isBlank() ? null : stripTrailingSlash(baseUrl.strip());
    }

    /**
     * ベースURL（末尾の {@code /} なし）を返す。
     *
     * @param request 要求
     * @return ベースURL
     */
    public String resolve(HttpServletRequest request) {
        if (configuredBaseUrl != null) {
            return configuredBaseUrl;
        }
        String scheme = request.getScheme().toLowerCase(Locale.ROOT);
        String host = request.getServerName();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        int port = request.getServerPort();
        boolean defaultPort =
                port <= 0 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
