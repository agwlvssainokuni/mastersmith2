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
package cherry.mastersmith.auth.web;

import cherry.mastersmith.auth.domain.ClientInfo;
import cherry.mastersmith.common.observability.TraceIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * 要求から送り手の情報を作る（BR7.1）。接続元IPは要求の接続元（転送元のヘッダーは、U1 の信頼の設定があるときだけ
 * {@code ForwardedHeaderFilter} が反映したもの）。User-Agent は 512 文字で切る。トレースIDは U1 の仕組みから得る。
 */
@Component
public class ClientInfoResolver {

    private final TraceIdProvider traceIdProvider;

    /**
     * 作る。
     *
     * @param traceIdProvider 要求中のトレースIDの参照
     */
    public ClientInfoResolver(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * 要求から送り手の情報を作る。
     *
     * @param request 要求
     * @return 送り手の情報
     */
    public ClientInfo resolve(HttpServletRequest request) {
        return new ClientInfo(
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                traceIdProvider.currentTraceId().orElse(null));
    }
}
