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
//
// 失敗の知らせの文（functional-spec.md の W10、D4・D10）。一覧の上の失敗の知らせと、招待の Modal の中の知らせで共に使う。
// 文言の鍵ならその文言、503 の使えない理由なら W3 の理由の文。サーバーの detail は扱わない。
import { UnavailableReasonsText } from './InvitationUnavailableAlert'
import type { FailureNotice } from './useInvitationAdmin'
import { useInvitationText } from './useInvitationText'

/** 失敗の知らせの文 */
export function FailureNoticeText({ notice }: { notice: FailureNotice }) {
  const t = useInvitationText()
  return notice.kind === 'message' ? (
    <span>{t(notice.key)}</span>
  ) : (
    <UnavailableReasonsText reasons={notice.reasons} />
  )
}
