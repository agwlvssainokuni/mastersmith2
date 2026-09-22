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
package cherry.mastersmith.auth.domain;

/**
 * 要求の送り手の情報（出来事に載せる。BR7.1）。
 *
 * @param sourceIp 接続元IP
 * @param userAgent User-Agent（512 文字で切る。無ければ null）
 * @param traceId トレースID（無ければ null）
 */
public record ClientInfo(String sourceIp, String userAgent, String traceId) {

    /** User-Agent の長さの上限。 */
    public static final int MAX_USER_AGENT_LENGTH = 512;

    /** User-Agent を上限の長さで切る。 */
    public ClientInfo {
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
    }
}
