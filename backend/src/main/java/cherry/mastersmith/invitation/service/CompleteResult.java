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
package cherry.mastersmith.invitation.service;

import cherry.mastersmith.user.domain.FieldError;
import java.util.List;

/** 登録の完了の結果（契約 C6 の POST complete）。拒否の理由は監査にだけ渡し、ここには持たない（BR7.5・BR7.6）。 */
public sealed interface CompleteResult {

    /** 登録を完了した。 */
    record Completed() implements CompleteResult {}

    /**
     * 入力の誤り（招待は消費していない。BR7.2）。
     *
     * @param errors 項目ごとの誤り
     */
    record Invalid(List<FieldError> errors) implements CompleteResult {}

    /** 使えないリンク（期限切れ・使用済み・取り消し済み・置き換え済み・存在しない・同じメールアドレスの利用者がいる）。 */
    record Rejected() implements CompleteResult {}
}
