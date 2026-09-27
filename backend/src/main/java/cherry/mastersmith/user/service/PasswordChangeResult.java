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
package cherry.mastersmith.user.service;

import cherry.mastersmith.user.domain.FieldError;
import java.util.List;

/**
 * パスワードの変更の結果（想定内の失敗は例外にせず、この型で返す。HTTP の状態は画面入出力の層が決める）。
 */
public sealed interface PasswordChangeResult {

    /** 変えた（BR4.3）。 */
    record Changed() implements PasswordChangeResult {}

    /**
     * 入力の誤り（照合せず、監査もしていない。BR4.1）。
     *
     * @param errors 項目ごとの誤り（1件以上、要求の項目の順）
     */
    record Invalid(List<FieldError> errors) implements PasswordChangeResult {

        /** 誤りの一覧を変えられない形で持つ。 */
        public Invalid {
            errors = List.copyOf(errors);
        }
    }

    /** 今のパスワードの誤り（照合の後にほかの変更が確定した場合を含む。BR4.2、{@code reliability-design.md} 2節）。 */
    record CurrentMismatch() implements PasswordChangeResult {}

    /** 認証の後に本人の行が消えていた。 */
    record UserNotFound() implements PasswordChangeResult {}
}
