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
package cherry.mastersmith.dslmanage.web;

import cherry.mastersmith.auth.domain.AuthenticatedUser;
import cherry.mastersmith.common.web.RequestSizeRejection;
import cherry.mastersmith.common.web.RequestSizeRejectionListener;
import cherry.mastersmith.dslmanage.domain.DslProblemTypes;
import cherry.mastersmith.dslmanage.domain.DslSource;
import cherry.mastersmith.dslmanage.service.DslLifecycle;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 投入の API を本文の大きさの上限で断ったことを受け、受け付けなかった投入（理由 {@code SIZE_LIMIT}、識別なし、操作した人つき）の
 * 監査の出来事と指標（{@code submit}・{@code rejected}）を出す（決定 A、BR7.4、AC2.3.10・AC6.3.1）。
 *
 * <p>本文の上限の確かめは認証・認可の後にあるため、ここに届くのはログインした管理者の要求だけで、操作した人が分かる。本文は読まない。
 * 出どころは問い合わせの文字列（{@code source=UPLOAD|PASTE}）だけから取り、本文から読む形（フォーム）の要求の引数は見ない。
 */
@Component
public class DslSubmitRejectionListener implements RequestSizeRejectionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DslSubmitRejectionListener.class);

    private final DslLifecycle lifecycle;

    private final DslRequestContextResolver contextResolver;

    /**
     * 作る。
     *
     * @param lifecycle DSL の管理の業務処理
     * @param contextResolver 要求の文脈の組み立て
     */
    public DslSubmitRejectionListener(DslLifecycle lifecycle, DslRequestContextResolver contextResolver) {
        this.lifecycle = lifecycle;
        this.contextResolver = contextResolver;
    }

    @Override
    public void onRejected(HttpServletRequest request, RequestSizeRejection rejection) {
        if (rejection.route() == null || rejection.problemType() != DslProblemTypes.DSL_TOO_LARGE) {
            return;
        }
        Optional<AuthenticatedUser> user = contextResolver.currentUser();
        if (user.isEmpty()) {
            // 認可の後に置いているため起きない想定。黙って捨てず、記録できなかったことを WARN で出す。
            LOGGER.atWarn().log("操作した人が分からないため、大きさで断った投入を監査に記録できません");
            return;
        }
        lifecycle.recordOversizedSubmission(
                source(request.getQueryString()), contextResolver.resolve(request, user.get()));
    }

    /**
     * 問い合わせの文字列から出どころ（UPLOAD・PASTE）を取り出す。
     *
     * @param queryString 問い合わせの文字列（無ければ null）
     * @return 出どころ（無い・投入の出どころでなければ null）
     */
    static DslSource source(String queryString) {
        if (queryString == null) {
            return null;
        }
        String value = UriComponentsBuilder.fromUriString("?" + queryString)
                .build()
                .getQueryParams()
                .getFirst("source");
        if ("UPLOAD".equals(value)) {
            return DslSource.UPLOAD;
        }
        if ("PASTE".equals(value)) {
            return DslSource.PASTE;
        }
        return null;
    }
}
