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
package cherry.mastersmith.access.web;

import cherry.mastersmith.access.domain.AccessProblemTypes;
import cherry.mastersmith.common.security.ErrorResponseWriter;
import cherry.mastersmith.config.SecurityHeaderProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;
import org.springframework.stereotype.Component;

/**
 * 正規化されていないパスの要求の拒否の処理（BR1.6、BR6.1、NFR3.3、NFR3.6）。
 *
 * <p>Spring Security の要求の検査（エンコードされた区切り、{@code ;}、{@code ..}、{@code //} などを含むパスの拒否）は既定の
 * まま外さずに使い、その拒否を U1 の {@link ErrorResponseWriter} で 400 / {@code REQUEST_REJECTED} の共通の形にする。
 *
 * <p>この拒否は U1 のヘッダーを書く処理より手前で起きるため、U1 の設定の値で同じヘッダー（CSP・{@code X-Content-Type-Options}・
 * {@code X-Frame-Options}・{@code Referrer-Policy}）をここで付ける。{@code Cache-Control: no-store} は U1 の
 * {@code CacheControlFilter} が連鎖より前で付けるため、ここでは付けない。
 *
 * <p>WARN には {@code code} だけを出し、**拒否したパスは出さない**（細工された文字列を記録に入れないため。
 * {@code security-design.md} 2章）。トレースIDは U1 のログの仕組みが同じ行に付けるため、ここでは重ねて出さない
 * （{@code observability-design.md} 2章）。判定の前の拒否であるため、アクセス拒否の出来事は作らない。
 */
@Component
public class AccessRequestRejectedHandler implements RequestRejectedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessRequestRejectedHandler.class);

    private final ErrorResponseWriter errorResponseWriter;

    private final SecurityHeaderProperties headerProperties;

    /**
     * 作る。
     *
     * @param errorResponseWriter フィルターの段階のエラー応答の書き方（U1）
     * @param headerProperties 応答のヘッダーの設定（U1）
     */
    public AccessRequestRejectedHandler(
            ErrorResponseWriter errorResponseWriter, SecurityHeaderProperties headerProperties) {
        this.errorResponseWriter = errorResponseWriter;
        this.headerProperties = headerProperties;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, RequestRejectedException requestRejectedException)
            throws IOException {
        response.setHeader("Content-Security-Policy", headerProperties.contentSecurityPolicy());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", XFrameOptionsMode.DENY.name());
        response.setHeader("Referrer-Policy", ReferrerPolicy.SAME_ORIGIN.getPolicy());
        LOGGER.atWarn()
                .addKeyValue("code", AccessProblemTypes.REQUEST_REJECTED.code())
                .log("正規化されていないパスの要求を拒否しました");
        errorResponseWriter.write(request, response, AccessProblemTypes.REQUEST_REJECTED);
    }
}
