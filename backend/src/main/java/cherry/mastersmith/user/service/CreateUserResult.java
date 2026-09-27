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

/** 利用者の作成の結果（契約 C2、BR5.2）。想定内の失敗（登録済みのメールアドレス）は例外にせず、この型で返す。 */
public sealed interface CreateUserResult {

    /**
     * 作った。
     *
     * @param userId 作った利用者の ID
     */
    record Created(long userId) implements CreateUserResult {}

    /**
     * そろえたメールアドレスの利用者がすでにいるため、作らなかった（同時の作成で一意の制約に当たった場合を含む）。呼び出し元は今の
     * トランザクションを必ず巻き戻す（巻き戻しの印が付いている）。
     */
    record EmailAlreadyUsed() implements CreateUserResult {}
}
