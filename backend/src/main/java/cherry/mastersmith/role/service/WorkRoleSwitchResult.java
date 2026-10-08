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
package cherry.mastersmith.role.service;

/** 作業ロールの切り替えの結果（契約 C8 の {@code PUT /api/me/work-role}、FS の 2.10、BR7.5〜BR7.8）。文字列は種類だけ。 */
public sealed interface WorkRoleSwitchResult
        permits WorkRoleSwitchResult.Switched,
                WorkRoleSwitchResult.Unchanged,
                WorkRoleSwitchResult.NotAssigned,
                WorkRoleSwitchResult.Busy {

    /** 保存を書いた（204。監査に {@code WORK_ROLE_SWITCHED}）。 */
    record Switched() implements WorkRoleSwitchResult {}

    /** 保存がすでに選んだロールを指していた（204。書かない、監査なし。BR7.6）。 */
    record Unchanged() implements WorkRoleSwitchResult {}

    /** 割り当ての外・存在しないロール（409 {@code ROLE_NOT_ASSIGNED}。監査に FAILURE。BR7.5）。 */
    record NotAssigned() implements WorkRoleSwitchResult {}

    /** 同じ利用者の保存の主キーの待ちの上限切れか違反（409 {@code ROLE_BUSY}。監査なし。BR8.4）。 */
    record Busy() implements WorkRoleSwitchResult {}
}
