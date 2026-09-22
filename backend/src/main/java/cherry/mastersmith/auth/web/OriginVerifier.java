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

import cherry.mastersmith.auth.domain.AuthProblemTypes;
import cherry.mastersmith.common.error.domain.BusinessException;
import cherry.mastersmith.common.error.web.ProblemBaseUrlResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * 更新とログアウトの前の Origin の確認（NFR5.4）。要求の {@code Origin} を、U1 のエラー応答のベースURLと同じ決め方の自分の
 * 配信元と比べ、無い・一致しないなら 403 / {@code ORIGIN_NOT_ALLOWED}。WARN には {@code originPresent} だけを出す（Cookie の
 * 値は出さない）。
 */
@Component
public class OriginVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginVerifier.class);

    private final ProblemBaseUrlResolver baseUrlResolver;

    /**
     * 作る。
     *
     * @param baseUrlResolver 自分の配信元の決め方
     */
    public OriginVerifier(ProblemBaseUrlResolver baseUrlResolver) {
        this.baseUrlResolver = baseUrlResolver;
    }

    /**
     * 要求の Origin を確かめる。
     *
     * @param request 要求
     * @throws BusinessException 無い・一致しないとき（{@code ORIGIN_NOT_ALLOWED}）
     */
    public void verify(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && origin.equalsIgnoreCase(baseUrlResolver.resolve(request))) {
            return;
        }
        LOGGER.atWarn().addKeyValue("originPresent", origin != null).log("要求の送り元が自分の配信元と一致しないため拒否しました");
        throw new BusinessException(AuthProblemTypes.ORIGIN_NOT_ALLOWED);
    }
}
