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
package cherry.mastersmith.mail.transport;

import cherry.mastersmith.mail.domain.MailSendResult;
import java.util.Objects;

/**
 * 1回の送信の試みの結果。送信の結果と、失敗したときの例外の型の名前（ログの exceptionType。BR6.2）だけを持つ。
 *
 * @param result 送信の結果
 * @param exceptionType 失敗の例外の型の名前（成功・例外の無い失敗のときは null）
 */
public record SendAttempt(MailSendResult result, String exceptionType) {

    /** 必須の値を確かめる。 */
    public SendAttempt {
        Objects.requireNonNull(result, "result は必須です");
    }
}
