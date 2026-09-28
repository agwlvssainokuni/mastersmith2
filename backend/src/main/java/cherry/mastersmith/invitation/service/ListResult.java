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

/** 一覧の結果（契約 C5 の GET）。 */
public sealed interface ListResult {

    /**
     * 読んだ。
     *
     * @param page 1ページ
     */
    record Listed(InvitationPage page) implements ListResult {}

    /** page が 1 以上の整数でない（BR5.2）。 */
    record InvalidPage() implements ListResult {}
}
