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
// 招待を使えないときの警告（functional-spec.md の W3、AC1.1.6・AC2.2.9）。一覧の応答の invitationEnabled が偽のときだけ、
// make-you-chic-ui の Alert の警告の種類で描く。文の形は unavailable.ts で決め、知らない理由の値と設定の値は出さない。
import { Alert } from 'make-you-chic-ui'
import type { InvitationPage } from './api/types'
import { unavailableNotice } from './unavailable'
import { useInvitationText } from './useInvitationText'
import './InvitationUnavailableAlert.css'

export interface InvitationUnavailableAlertProps {
  /** 最後に使った一覧の応答（まだ無ければ null。そのときは描かない） */
  list: Pick<InvitationPage, 'invitationEnabled' | 'unavailableReasons'> | null
}

/**
 * 使えない理由の文（警告と、送り直しの 503 の失敗の知らせ・招待の 503 の Modal の中の知らせで共に使う）。
 * 理由が1つならその文、2つなら見出しと理由の並び、知っている理由が無ければ見出しと一般の文。
 */
export function UnavailableReasonsText({ reasons }: { reasons: readonly unknown[] }) {
  const t = useInvitationText()
  const notice = unavailableNotice(reasons)
  if (notice.kind === 'single') {
    return <span>{t(notice.messageKey)}</span>
  }
  return (
    <span className="invitation-reasons">
      <span className="invitation-reasons-title">{t(notice.titleKey)}</span>
      {notice.kind === 'multiple' ? (
        <span className="invitation-reason-list">
          {notice.reasonKeys.map((key) => (
            <span key={key} className="invitation-reason">
              {t(key)}
            </span>
          ))}
        </span>
      ) : (
        <span className="invitation-reason">{t(notice.messageKey)}</span>
      )}
    </span>
  )
}

/** 招待を使えないときの警告 */
export function InvitationUnavailableAlert({ list }: InvitationUnavailableAlertProps) {
  if (list === null || list.invitationEnabled) {
    return null
  }
  return (
    <div data-testid="invitation-unavailable-alert">
      <Alert variant="warning">
        <UnavailableReasonsText reasons={list.unavailableReasons} />
      </Alert>
    </div>
  )
}
