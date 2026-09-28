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
package cherry.mastersmith.invitation.domain;

/** 招待を使えない理由（BR1.4、契約 C5 の unavailableReasons）。並びはこの宣言の順（ベース URL が先）。 */
public enum UnavailableReason {
    /** 招待のリンクのベース URL が無い・形が合わない。 */
    BASE_URL_NOT_CONFIGURED,
    /** メールの送信の設定が無い（U1 の {@code isConfigured} が偽）。 */
    SMTP_NOT_CONFIGURED
}
