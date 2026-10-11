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
package cherry.mastersmith.navigation.testsupport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 測る要求に印を付けるテストだけのフィルター（{@code logical-components.md} の T1、{@code scalability-design.md} の 2節）。要求の頭
 * {@value #HEADER} を受けたときだけ、要求の始まりに印を {@link ConnectionCountingDataSource#begin(String)} で置き、{@code finally} で
 * 消す。認証のフィルターより前に動くよう、最も高い順で登録する（{@link ConnectionCountingConfig}）。
 */
public final class MeasureIdFilter extends OncePerRequestFilter {

    /** 測る要求の印の頭。 */
    public static final String HEADER = "X-Test-Measure-Id";

    private final ConnectionCountingDataSource counter;

    /**
     * 作る。
     *
     * @param counter 接続の数えの包み
     */
    public MeasureIdFilter(ConnectionCountingDataSource counter) {
        this.counter = counter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String measureId = request.getHeader(HEADER);
        if (measureId == null || measureId.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        counter.begin(measureId);
        try {
            chain.doFilter(request, response);
        } finally {
            counter.end();
        }
    }
}
