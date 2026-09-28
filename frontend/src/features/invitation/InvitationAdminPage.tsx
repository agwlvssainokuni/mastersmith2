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
// 招待の管理の画面（S1、frontend-components.md の 1節・4節、functional-spec.md の W1〜W10、mockups.md の S1）。
// 見出し（h1）と「招待する」、招待を使えないときの警告、一覧の上の失敗の知らせ、招待中の人の一覧、招待の入力と取り消しの
// 確かめの Modal、件数の範囲の読み上げの領域を並べる。状態と操作は useInvitationAdmin が持ち、この部品は子の部品に値と操作を
// 渡すだけ。ページ送りで URL を変えない（navigate・history を呼ばない）。管理者だけに出す判定はサーバー側で行う（D14）。
import { Alert, Button } from 'make-you-chic-ui'
import { useDisplaySettings } from '../../app/display-settings/DisplaySettingsProvider'
import { invitationApi, type InvitationApi } from './api/invitationApi'
import { CancelConfirmDialog } from './CancelConfirmDialog'
import { FailureNoticeText } from './FailureNoticeText'
import { InvitationList } from './InvitationList'
import { InvitationUnavailableAlert } from './InvitationUnavailableAlert'
import { InviteDialog } from './InviteDialog'
import { useInvitationAdmin } from './useInvitationAdmin'
import { useInvitationText } from './useInvitationText'
import './InvitationAdminPage.css'

export interface InvitationAdminPageProps {
  /** API の関数の集まり（テストで差し替える） */
  api?: InvitationApi
  /** 日時の時差（省略すると端末の時差。テストで固定する） */
  timeZone?: string
}

/** 招待の管理の画面 */
export function InvitationAdminPage({ api = invitationApi, timeZone }: InvitationAdminPageProps) {
  const t = useInvitationText()
  const { language } = useDisplaySettings()
  const state = useInvitationAdmin(api, t, language)
  const invitationEnabled = state.list?.invitationEnabled ?? false
  const canInvite = state.list !== null && invitationEnabled && state.loadState !== 'failed'

  return (
    <div className="invitation-admin" data-testid="invitation-admin-page">
      <div className="invitation-admin-header">
        <h1 className="invitation-admin-heading">{t('invitation.title')}</h1>
        <Button
          variant="primary"
          disabled={!canInvite}
          onClick={state.openInvite}
          data-testid="invitation-invite-button"
        >
          {t('invitation.action.invite')}
        </Button>
      </div>
      <InvitationUnavailableAlert list={state.list} />
      {state.failure !== null && (
        <div data-testid="invitation-failure-alert">
          <Alert
            variant="danger"
            onDismiss={state.dismissFailure}
            dismissLabel={t('invitation.action.dismissAlert')}
          >
            <FailureNoticeText notice={state.failure} />
          </Alert>
        </div>
      )}
      <InvitationList
        list={state.list}
        page={state.page}
        loadState={state.loadState}
        loadFailureKey={state.loadFailureKey}
        invitationEnabled={invitationEnabled}
        resendingIds={state.resendingIds}
        highlightedId={state.highlightedId}
        focusTarget={state.focusTarget}
        language={language}
        timeZone={timeZone}
        onResend={state.resend}
        onRevoke={state.requestCancel}
        onRetry={state.retry}
        onPageChange={state.goToPage}
        onFocusApplied={state.onFocusApplied}
      />
      <InviteDialog
        state={state.invite}
        onEmailChange={state.changeInviteEmail}
        onLanguageChange={state.changeInviteLanguage}
        onSubmit={state.submitInvite}
        onClose={state.closeInvite}
        onShowPendingRow={state.showPendingRow}
      />
      <CancelConfirmDialog
        target={state.cancelTarget}
        busy={state.cancelBusy}
        onConfirm={state.confirmCancel}
        onClose={state.closeCancel}
      />
      <div
        aria-live="polite"
        className="invitation-visually-hidden"
        data-testid="invitation-live-region"
      >
        {state.liveMessage}
      </div>
    </div>
  )
}
