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
package cherry.mastersmith.businessdata.domain;

import java.util.Objects;

/**
 * 書き込みの要求の送り手の情報（Intent 261016-business-data-views の U2、監査の出来事に使う。entities.md の
 * BusinessDataChangedEvent.sourceIp・userAgent・requestPath・traceId）。businessdata は user に依存しないため、user の
 * {@code RequestOrigin} と同じ形を自分で持つ。
 *
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（無ければ null。上限の長さで切る）
 * @param requestPath 要求の道（固定の道のため値を含まない。無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record WriteOrigin(String sourceIp, String userAgent, String requestPath, String traceId) {

    /** User-Agent の長さの上限（既存の監査の送り手の情報と同じ）。 */
    public static final int MAX_USER_AGENT_LENGTH = 512;

    /** 接続元IPが有ることを確かめ、User-Agent を上限の長さで切る。 */
    public WriteOrigin {
        Objects.requireNonNull(sourceIp, "sourceIp は必須です");
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
    }
}
