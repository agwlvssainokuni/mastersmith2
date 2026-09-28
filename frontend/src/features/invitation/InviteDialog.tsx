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
// 招待の入力（S1-M1、functional-spec.md の W4〜W7、D8・D9・D12、frontend-components.md の 4節）。
// make-you-chic-ui の Modal（背景のクリックで閉じない、閉じるボタンの名前は画面の言語、はじめのフォーカスはメールアドレス）。
// 送信中は Esc・閉じるボタン・「やめる」で閉じない。「招待する」は Button の loading（aria-disabled・aria-busy）で
// 「送信しています」と示し、フォーカスを保つ。ブラウザの形式の確かめは使わない（noValidate。形式はサーバーが判定する）。
// 誤りを出したら、描いた後にメールアドレスへフォーカスを移す。招待中の誤りで行の位置が読めたときは「一覧でこの招待を見る」
// （画面の中の操作のため button。見た目はリンク）を出す。
import { Alert, Button, FormField, Modal, RadioGroup, TextInput } from 'make-you-chic-ui'
import { useEffect, useRef, type FormEvent } from 'react'
import { LANGUAGE_NAMES } from '../../app/display-settings/displaySettingsTypes'
import { INVITATION_LANGUAGES, type InvitationLanguage } from './api/types'
import { FailureNoticeText } from './FailureNoticeText'
import type { InviteState } from './useInvitationAdmin'
import { useInvitationText } from './useInvitationText'
import './InviteDialog.css'

export interface InviteDialogProps {
  /** 招待の入力の状態（閉じているときは null） */
  state: InviteState | null
  onEmailChange: (value: string) => void
  onLanguageChange: (value: InvitationLanguage) => void
  onSubmit: () => void
  onClose: () => void
  onShowPendingRow: () => void
}

function isInvitationLanguage(value: string): value is InvitationLanguage {
  return (INVITATION_LANGUAGES as readonly string[]).includes(value)
}

/** 招待の入力 */
export function InviteDialog({
  state,
  onEmailChange,
  onLanguageChange,
  onSubmit,
  onClose,
  onShowPendingRow,
}: InviteDialogProps) {
  const t = useInvitationText()
  const emailRef = useRef<HTMLInputElement>(null)
  const errorSeq = state?.errorSeq ?? 0
  const hasFieldError = state?.fieldError != null

  // 誤りを出したら（同じ誤りを重ねて出したときも）、描いた後にメールアドレスへフォーカスを移す。
  useEffect(() => {
    if (hasFieldError) {
      emailRef.current?.focus()
    }
  }, [errorSeq, hasFieldError])

  if (state === null) {
    return null
  }
  const busy = state.busy

  function handleClose(): void {
    if (!busy) {
      onClose()
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()
    if (!busy) {
      onSubmit()
    }
  }

  return (
    <Modal
      open
      title={t('invitation.invite.title')}
      onClose={handleClose}
      initialFocusRef={emailRef}
      closeLabel={t('invitation.action.close')}
      closeOnBackdropClick={false}
    >
      <form
        className="invitation-invite"
        noValidate
        onSubmit={handleSubmit}
        data-testid="invitation-invite-dialog"
      >
        {state.dialogAlert !== null && (
          <div data-testid="invitation-invite-dialog-alert">
            <Alert variant="danger">
              <FailureNoticeText notice={state.dialogAlert} />
            </Alert>
          </div>
        )}
        <FormField
          label={t('invitation.invite.email')}
          error={state.fieldError !== null ? t(state.fieldError) : undefined}
        >
          <TextInput
            ref={emailRef}
            type="email"
            autoComplete="off"
            aria-required="true"
            value={state.email}
            onChange={onEmailChange}
            data-testid="invitation-invite-email-input"
          />
        </FormField>
        {state.pending !== null && (
          <div>
            <button
              type="button"
              className="invitation-link-button"
              onClick={onShowPendingRow}
              data-testid="invitation-invite-show-row"
            >
              {t('invitation.action.showRow')}
            </button>
          </div>
        )}
        <div className="invitation-invite-language" data-testid="invitation-invite-language">
          <RadioGroup
            name="invitation-language"
            legend={t('invitation.invite.language')}
            options={INVITATION_LANGUAGES.map((value) => ({
              value,
              label: LANGUAGE_NAMES[value],
              lang: value,
            }))}
            value={state.language}
            onChange={(value) => {
              if (isInvitationLanguage(value)) {
                onLanguageChange(value)
              }
            }}
          />
          <p className="invitation-hint">{t('invitation.invite.languageHint')}</p>
        </div>
        <div className="invitation-dialog-actions">
          <Button
            variant="secondary"
            disabled={busy}
            onClick={handleClose}
            data-testid="invitation-invite-cancel"
          >
            {t('invitation.action.cancel')}
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={busy}
            data-testid="invitation-invite-submit"
          >
            {t(busy ? 'invitation.action.sending' : 'invitation.action.invite')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
