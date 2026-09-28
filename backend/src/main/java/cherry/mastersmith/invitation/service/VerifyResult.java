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

/** リンクの確かめの結果（契約 C6 の POST verify）。拒否の理由は持たない（理由によらず同じ応答。BR7.5）。 */
public sealed interface VerifyResult {

    /**
     * 使えるリンク。
     *
     * @param view 画面の表示に要る値
     */
    record Valid(InvitationView view) implements VerifyResult {}

    /** 使えないリンク。 */
    record Rejected() implements VerifyResult {}
}
