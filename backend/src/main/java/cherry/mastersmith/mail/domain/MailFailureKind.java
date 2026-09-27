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
package cherry.mastersmith.mail.domain;

/** 送信の失敗の種類（契約 C1、BR1.7・BR3.x・BR4.x・BR5.3）。 */
public enum MailFailureKind {
    /** SMTP の設定が無い・不正のため送らない（BR1.7）。接続しない。 */
    NOT_CONFIGURED,
    /** 接続できない・暗号化を確立できない・途中で接続が切れた（BR5.3）。 */
    CONNECTION_FAILED,
    /** 接続または応答の待ちが時間切れになった（BR5.2・BR5.3）。 */
    TIMEOUT,
    /** 受け手が拒否の応答を返した（認証・差出人・宛先・本文。BR5.3）。 */
    REJECTED,
    /** 依頼の誤り（言語・宛先の形・改行・差し込みの名前と値。BR3.1〜BR3.4）。接続しない。 */
    INVALID_INPUT,
    /** テンプレートの誤り（一覧に無い templateId・描く途中の失敗・件名や lang の誤り。BR3.4・BR4.2〜BR4.4）。接続しない。 */
    TEMPLATE_ERROR
}
