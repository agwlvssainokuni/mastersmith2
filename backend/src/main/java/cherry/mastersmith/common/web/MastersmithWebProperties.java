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
package cherry.mastersmith.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Web の設定（{@code mastersmith.web.*}）。
 *
 * @param baseUrl エラー応答の type の URL の元になるベースURL（無ければ要求から組み立てる）
 * @param trustForwardedHeaders 転送元のヘッダー（X-Forwarded-*・Forwarded）を信頼するか（既定は信頼しない）
 * @param maxRequestBodySize 要求の本文の大きさの上限（既定 1MB）
 */
@ConfigurationProperties("mastersmith.web")
public record MastersmithWebProperties(
        String baseUrl,
        @DefaultValue("false") boolean trustForwardedHeaders,
        @DefaultValue("1MB") DataSize maxRequestBodySize) {}
