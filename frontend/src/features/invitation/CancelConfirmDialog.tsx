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
// 取り消しの確かめ（S1-M2、functional-spec.md の W9、D9・D12、frontend-components.md の 4節）。
// make-you-chic-ui の Modal（role="alertdialog"、背景のクリックで閉じない、閉じるボタンの名前は画面の言語、
// はじめのフォーカスは「やめる」）。本文は Modal がダイアログの説明（aria-describedby）として結ぶ。
// 要求中は閉じない。「取り消す」は Button の loading で「取り消しています」、「やめる」は押せない。
// 閉じるとフォーカスは開く前の行の「取り消す」へ戻る（Modal の決まり）。
import { Button, Modal } from 'make-you-chic-ui'
import { useRef } from 'react'
import type { Invitation } from './api/types'
import { useInvitationText } from './useInvitationText'
import './InviteDialog.css'

export interface CancelConfirmDialogProps {
  /** 取り消しの確かめの対象（閉じているときは null） */
  target: Invitation | null
  /** 取り消しの要求中 */
  busy: boolean
  onConfirm: () => void
  onClose: () => void
}

/** 取り消しの確かめ */
export function CancelConfirmDialog({
  target,
  busy,
  onConfirm,
  onClose,
}: CancelConfirmDialogProps) {
  const t = useInvitationText()
  const cancelRef = useRef<HTMLButtonElement>(null)

  if (target === null) {
    return null
  }

  function handleClose(): void {
    if (!busy) {
      onClose()
    }
  }

  return (
    <Modal
      open
      role="alertdialog"
      title={t('invitation.revoke.title')}
      onClose={handleClose}
      initialFocusRef={cancelRef}
      closeLabel={t('invitation.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="invitation-invite" data-testid="invitation-revoke-dialog">
        <p className="invitation-dialog-text">
          {t('invitation.revoke.body', { email: target.email })}
        </p>
        <div className="invitation-dialog-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            disabled={busy}
            onClick={handleClose}
            data-testid="invitation-revoke-cancel"
          >
            {t('invitation.action.cancel')}
          </Button>
          <Button
            variant="danger"
            loading={busy}
            onClick={onConfirm}
            data-testid="invitation-revoke-confirm"
          >
            {t(busy ? 'invitation.action.revoking' : 'invitation.action.revoke')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
